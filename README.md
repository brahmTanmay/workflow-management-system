# Leave Management System — Workforce Management Platform

A production-grade leave management core built with **Dropwizard**, **Hibernate**, and **H2**. Handles multi-type leave policies, per-employee cycles, carry-forward pools, concurrent approvals, retroactive policy corrections, and a fully reconstructable audit ledger.

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    JAX-RS Resources                     │
│  BalanceResource · LeaveRequestResource · AuditResource │
│  ManagerDashboardResource · AdminResource               │
├─────────────────────────────────────────────────────────┤
│                      Services                           │
│  LeaveAccrualService · LeaveBalanceService               │
│  LeaveRequestService · CarryForwardService               │
│  PolicyChangeService · LeaveCycleCalculator              │
├─────────────────────────────────────────────────────────┤
│                   DAOs (Hibernate)                       │
│  EmployeeDAO · LeavePoolDAO · LeaveRequestDAO            │
│  LeavePolicyDAO · LeaveLedgerDAO · EnrollmentDAO         │
├─────────────────────────────────────────────────────────┤
│                   Domain Model                          │
│  Employee · Team · LeavePolicy (versioned)               │
│  LeavePool (dual-pool) · LeaveRequest · LeaveLedgerEntry │
└─────────────────────────────────────────────────────────┘
```

---

## Core Design Decisions

### 1. Event-Sourced Audit Ledger

Every balance mutation — accrual, hold, deduction, release, carry-forward, policy adjustment — is recorded as an **immutable** `LeaveLedgerEntry` with a signed delta and running balance. The full balance at any point in time can be reconstructed by replaying ledger entries up to a given timestamp.

```
ACCRUAL +10.42 → HOLD -5.00 → DEDUCTION -5.00 → HOLD_RELEASE +5.00
```

### 2. Dual-Pool Model

Each employee holds **two pools per leave type** at any time:

| Pool | Purpose |
|------|---------|
| `CURRENT_CYCLE` | Days accrued/granted in the current leave cycle |
| `CARRY_FORWARD` | Leftover days from the previous cycle, with its own expiry date |

When deducting leave, the system draws from **carry-forward first** (earliest expiry), then current cycle. A single deduction can split across both pools.

### 3. Hold / Reserve Pattern

```
Submit Request  →  Days HELD (available decreases, used unchanged)
Approve         →  Held → Used (deduction)
Reject / Cancel →  Held → Released (balance restored)
```

This prevents overbooking — if Bob has 10 available days and submits a 7-day request, only 3 days remain available for further requests, even before the manager acts.

### 4. Optimistic Locking for Concurrency

`LeavePool` uses `@Version` (Hibernate optimistic locking). If two managers approve leave for the same employee simultaneously, one transaction will fail with `OptimisticLockException` rather than silently double-deducting. The failed request can be retried safely.

### 5. Versioned Policies with Retroactive Correction

`LeavePolicy` uses a `(policyGroupId, version)` scheme. When a policy is updated:

1. Old version is deactivated
2. New version is created
3. All enrolled employees' pools are adjusted with a pro-rated delta
4. Ledger entries record the adjustment with full context

Example: Parental leave misconfigured as 60 days, corrected to 90 → all affected employees get a `POLICY_ADJUSTMENT +30` ledger entry.

### 6. Per-Employee Leave Cycles

Cycles are computed per-employee based on the policy's `CycleType`:

| CycleType | Boundary |
|-----------|----------|
| `CALENDAR_YEAR` | Jan 1 – Dec 31 |
| `EMPLOYEE_START_DATE` | Anniversary of hire date |
| `CUSTOM` | Configurable start month |

Two employees on the same policy can hold different balances on the same calendar day.

### 7. Multiple Accrual Methods

| Method | Behavior |
|--------|----------|
| `MONTHLY` | `entitlementDays / 12` credited each month (idempotent) |
| `YEARLY` | Full entitlement granted at cycle start |
| `ONE_TIME` | Single grant, never re-accrues (e.g. parental leave) |

---

## Data Model

```
Employee ──┬── LeavePool (CURRENT_CYCLE)  ──── LeaveLedgerEntry
           ├── LeavePool (CARRY_FORWARD)  ──── LeaveLedgerEntry
           ├── LeaveRequest
           └── EmployeePolicyEnrollment ──── LeavePolicy (v1, v2, ...)
```

Key entities:

- **Employee** — has `startDate`, self-referencing `managerId`
- **LeavePolicy** — versioned, defines accrual method, entitlement, carry-forward rules, tenure eligibility
- **LeavePool** — balance container with `credited`, `used`, `held` fields + optimistic lock version
- **LeaveRequest** — lifecycle: PENDING → APPROVED / REJECTED / CANCELLED
- **LeaveLedgerEntry** — immutable audit row with event type, signed delta, running balance, timestamp
- **EmployeePolicyEnrollment** — links employee to policy group

---

## API Endpoints

### Employee

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/employees/{id}/balance` | All leave balances |
| `GET` | `/api/employees/{id}/balance/{leaveType}` | Balance for specific type |
| `GET` | `/api/employees/{id}/leave-requests` | List all requests |
| `POST` | `/api/employees/{id}/leave-requests` | Submit leave request |
| `POST` | `/api/employees/{id}/leave-requests/{id}/approve` | Manager approves |
| `POST` | `/api/employees/{id}/leave-requests/{id}/reject` | Manager rejects |
| `POST` | `/api/employees/{id}/leave-requests/{id}/cancel` | Cancel request |

### Manager Dashboard

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/managers/{id}/dashboard/balances` | Team balances |
| `GET` | `/api/managers/{id}/dashboard/pending-requests` | Pending approvals |

### Audit

| Method | Endpoint | Description |
|--------|----------|-------------|
| `GET` | `/api/employees/{id}/audit` | Full audit trail |
| `GET` | `/api/employees/{id}/audit/{leaveType}` | Audit trail by type |
| `GET` | `/api/employees/{id}/audit/{leaveType}/at?timestamp=` | Balance at point-in-time |

### Admin

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/admin/accrual?asOfDate=` | Trigger accrual for all employees |
| `POST` | `/api/admin/accrual/{employeeId}?asOfDate=` | Trigger accrual for one employee |
| `POST` | `/api/admin/carry-forward/expire?asOfDate=` | Expire carry-forward pools |

---

## Running

```bash
# Build
mvn clean package

# Run (H2 in-memory DB, no external dependencies)
java -jar target/leave-management-1.0.0-SNAPSHOT.jar server config.yml
```

The server starts on `http://localhost:8080` with seeded test data:

| Employee | ID | Role | Policies |
|----------|----|------|----------|
| Alice Johnson | 1 | Manager | VACATION, SICK |
| Bob Smith | 2 | Reports to Alice | VACATION, SICK, PARENTAL |
| Carol Williams | 3 | Reports to Alice | VACATION, SICK |

---

## Tech Stack

- **Dropwizard 4.0** — JAX-RS (Jersey), Jackson, Jetty, Metrics
- **Hibernate 6** — JPA ORM with optimistic locking
- **H2** — In-memory database (swap to PostgreSQL/MySQL via config)
- **Lombok** — Boilerplate reduction
- **Java 17**
