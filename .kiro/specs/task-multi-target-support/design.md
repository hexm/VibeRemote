# Design Document: Task Multi-Target Support

## Overview

This design enhances the task management system to support native multi-agent execution. The current architecture has conceptual confusion where both "tasks" and "batch tasks" handle script distribution. This design implements the correct architecture where:

- **Task** = 1 script + N target agents (N≥1)
- **Batch Task (Workflow)** = Task 1 → Task 2 → Task 3 (with dependencies) - Phase 2, not covered here

The key insight is that multi-agent execution is a fundamental property of tasks, not a separate concept. This design refactors the data model and API to reflect this principle while maintaining backward compatibility.

## Architecture

### Current Architecture Problems

1. **Duplicate Concepts**: Both Task and BatchTask entities store script content and handle multi-agent execution
2. **Confusing UI**: Two separate tabs for "普通任务" and "批量任务" when they should be unified
3. **Inflexible Model**: Task.agentId is a single string, preventing native multi-agent support
4. **Incomplete Tracking**: No way to track individual agent execution status within a multi-agent task

### Proposed Architecture

```
Task (1) ----< (N) TaskExecution
  |
  +-- taskId (PK)
  +-- taskName
  +-- scriptLang, scriptContent, timeoutSec
  +-- createdBy, createdAt
  
TaskExecution (N)
  |
  +-- id (PK)
  +-- taskId (FK)
  +-- agentId
  +-- executionNumber
  +-- status, exitCode, logFilePath
  +-- startedAt, finishedAt
```

### Key Design Decisions

1. **Remove agentId from Task**: Task no longer stores a single agentId; instead, target agents are represented through TaskExecution records
2. **TaskExecution as First-Class Entity**: Each agent's execution is tracked independently with its own status, logs, and timing
3. **Aggregated Status**: Task status is computed from all TaskExecution statuses
4. **Execution History**: TaskExecution.executionNumber tracks restart attempts for the same task-agent combination
5. **UI Simplification**: Remove batch task tab; enhance task creation to support multi-select

## Components and Interfaces

### Backend Components

#### 1. Task Entity (Modified)

```java
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    private String taskId;
    
    // REMOVED: agentId field
    
    private String taskName;
    private String scriptLang;
    @Column(columnDefinition = "TEXT")
    private String scriptContent;
    private Integer timeoutSec;
    
    private String createdBy;
    private LocalDateTime createdAt;
    
    // Transient fields for aggregated status
    @Transient
    private String aggregatedStatus; // ALL_SUCCESS, PARTIAL_SUCCESS, ALL_FAILED, IN_PROGRESS, PENDING
    
    @Transient
    private Integer targetAgentCount;
    
    @Transient
    private Integer completedExecutions;
    
    @Transient
    private String executionProgress; // "3/5 completed"
}
```

#### 2. TaskExecution Entity (Enhanced)

```java
@Entity
@Table(name = "task_executions",
    uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "agent_id", "execution_number"})
)
public class TaskExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "task_id", nullable = false)
    private String taskId;
    
    @Column(name = "agent_id", nullable = false)
    private String agentId;
    
    @Column(name = "execution_number", nullable = false)
    private Integer executionNumber; // 1, 2, 3... for restarts
    
    private String status; // PENDING, PULLED, RUNNING, SUCCESS, FAILED, TIMEOUT, CANCELLED
    
    private String logFilePath;
    private Integer exitCode;
    private String summary;
    
    private LocalDateTime pulledAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private LocalDateTime createdAt;
}
```

#### 3. TaskService (Enhanced)

```java
public interface TaskService {
    // Create task with multiple agents
    String createTask(List<String> agentIds, TaskSpec taskSpec, String createdBy);
    
    // Get task with aggregated status
    Optional<TaskDTO> getTask(String taskId);
    
    // Get all execution instances for a task
    List<TaskExecution> getTaskExecutions(String taskId);
    
    // Get task summary (aggregated status and progress)
    TaskSummary getTaskSummary(String taskId);
    
    // Restart task (all or failed only)
    void restartTask(String taskId, RestartMode mode);
    
    // Cancel specific execution
    void cancelExecution(Long executionId);
    
    // Cancel all executions of a task
    void cancelTask(String taskId);
    
    // Get execution by ID
    Optional<TaskExecution> getTaskExecution(Long executionId);
}
```

#### 4. TaskDTO (New)

```java
public class TaskDTO {
    private String taskId;
    private String taskName;
    private String scriptLang;
    private String scriptContent;
    private Integer timeoutSec;
    private String createdBy;
    private LocalDateTime createdAt;
    
    // Aggregated fields
    private String aggregatedStatus;
    private Integer targetAgentCount;
    private Integer completedExecutions;
    private String executionProgress;
    
    // Execution breakdown
    private Integer pendingCount;
    private Integer runningCount;
    private Integer successCount;
    private Integer failedCount;
    private Integer timeoutCount;
    private Integer cancelledCount;
}
```

#### 5. RestartMode Enum (New)

```java
public enum RestartMode {
    ALL,          // Restart all executions
    FAILED_ONLY   // Restart only failed/timeout executions
}
```

### Frontend Components

#### 1. Tasks.jsx (Simplified)

```javascript
// Remove batch task tab
// Change agent selector to multi-select
// Update task list columns to show:
//   - Target agent count
//   - Execution progress (3/5 completed)
//   - Aggregated status

const Tasks = () => {
  const [tasks, setTasks] = useState([])
  const [onlineAgents, setOnlineAgents] = useState([])
  
  // Task creation with multi-select
  const handleCreateTask = async (values) => {
    const taskSpec = {
      scriptLang: values.scriptLang,
      scriptContent: values.scriptContent,
      timeoutSec: values.timeoutSec
    }
    
    await api.post('/web/tasks/create', taskSpec, {
      params: {
        agentIds: values.selectedAgents, // Array of agent IDs
        taskName: values.taskName
      }
    })
  }
  
  // View task detail with execution instances
  const handleViewTaskDetail = async (task) => {
    const executions = await api.get(`/web/tasks/${task.taskId}/executions`)
    // Show modal with execution instances table
  }
}
```

#### 2. TaskDetailModal (New Component)

```javascript
const TaskDetailModal = ({ task, visible, onClose }) => {
  const [executions, setExecutions] = useState([])
  const [filterStatus, setFilterStatus] = useState('all')
  
  // Load execution instances
  useEffect(() => {
    if (visible && task) {
      loadExecutions()
    }
  }, [visible, task])
  
  const loadExecutions = async () => {
    const data = await api.get(`/web/tasks/${task.taskId}/executions`)
    setExecutions(data)
  }
  
  // View log for specific execution
  const handleViewLog = async (execution) => {
    const logs = await api.get(`/web/tasks/executions/${execution.id}/logs`)
    // Show log modal
  }
  
  // Cancel specific execution
  const handleCancelExecution = async (execution) => {
    await api.post(`/web/tasks/executions/${execution.id}/cancel`)
    loadExecutions()
  }
  
  return (
    <Modal>
      <Table
        dataSource={executions.filter(e => 
          filterStatus === 'all' || e.status === filterStatus
        )}
        columns={[
          { title: 'Agent', dataIndex: 'agentId' },
          { title: 'Status', dataIndex: 'status' },
          { title: 'Started', dataIndex: 'startedAt' },
          { title: 'Finished', dataIndex: 'finishedAt' },
          { title: 'Exit Code', dataIndex: 'exitCode' },
          { title: 'Actions', render: (_, record) => (
            <Space>
              <Button onClick={() => handleViewLog(record)}>Log</Button>
              {record.status === 'RUNNING' && (
                <Button onClick={() => handleCancelExecution(record)}>Cancel</Button>
              )}
            </Space>
          )}
        ]}
      />
    </Modal>
  )
}
```

### API Endpoints

#### Modified Endpoints

**POST /api/web/tasks/create**
- Request: `?agentIds=id1&agentIds=id2&taskName=name` + TaskSpec body
- Response: `{ taskId, targetAgentCount }`
- Creates one Task and N TaskExecution records

**GET /api/web/tasks**
- Response: Page<TaskDTO> with aggregated status and progress
- Each TaskDTO includes targetAgentCount and executionProgress

**GET /api/web/tasks/{taskId}**
- Response: TaskDTO with full aggregated information

#### New Endpoints

**GET /api/web/tasks/{taskId}/executions**
- Response: List<TaskExecution>
- Returns all execution instances for a task

**GET /api/web/tasks/{taskId}/summary**
- Response: TaskSummary with aggregated status and execution breakdown
- Used for quick status checks without loading full execution list

**POST /api/web/tasks/{taskId}/restart**
- Request: `?mode=ALL|FAILED_ONLY`
- Response: `{ message, newExecutionCount }`
- Creates new TaskExecution records based on restart mode

**POST /api/web/tasks/executions/{executionId}/cancel**
- Response: `{ message }`
- Cancels a specific execution instance

**GET /api/web/tasks/executions/{executionId}/logs**
- Response: `{ content, totalLines, offset, limit, hasMore }`
- Returns logs for a specific execution instance

**GET /api/web/tasks/executions/{executionId}/download**
- Response: File download
- Downloads log file for a specific execution instance

## Data Models

### Database Schema Changes

#### Task Table (Modified)

```sql
CREATE TABLE tasks (
    task_id VARCHAR(64) PRIMARY KEY,
    -- REMOVED: agent_id column
    task_name VARCHAR(200),
    script_lang VARCHAR(20),
    script_content TEXT,
    timeout_sec INTEGER,
    created_by VARCHAR(64),
    created_at TIMESTAMP
);
```

#### TaskExecution Table (Enhanced)

```sql
CREATE TABLE task_executions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    execution_number INTEGER NOT NULL,
    status VARCHAR(20),
    log_file_path VARCHAR(500),
    exit_code INTEGER,
    summary TEXT,
    pulled_at TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP,
    
    FOREIGN KEY (task_id) REFERENCES tasks(task_id) ON DELETE CASCADE,
    UNIQUE KEY uk_task_agent_exec (task_id, agent_id, execution_number),
    INDEX idx_task_id (task_id),
    INDEX idx_agent_id (agent_id),
    INDEX idx_status (status)
);
```

### Data Migration Strategy

```sql
-- Step 1: Create new task_executions table
CREATE TABLE task_executions (...);

-- Step 2: Migrate existing tasks to task_executions
INSERT INTO task_executions (
    task_id, agent_id, execution_number, status,
    log_file_path, exit_code, summary,
    pulled_at, started_at, finished_at, created_at
)
SELECT 
    task_id, agent_id, 
    execution_count as execution_number,
    status, log_file_path, exit_code, summary,
    pulled_at, started_at, finished_at, created_at
FROM tasks
WHERE execution_count > 0;

-- Step 3: Remove agent_id column from tasks
ALTER TABLE tasks DROP COLUMN agent_id;
ALTER TABLE tasks DROP COLUMN batch_id;
ALTER TABLE tasks DROP COLUMN status;
ALTER TABLE tasks DROP COLUMN execution_count;
ALTER TABLE tasks DROP COLUMN log_file_path;
ALTER TABLE tasks DROP COLUMN exit_code;
ALTER TABLE tasks DROP COLUMN summary;
ALTER TABLE tasks DROP COLUMN pulled_at;
ALTER TABLE tasks DROP COLUMN started_at;
ALTER TABLE tasks DROP COLUMN finished_at;

-- Step 4: Drop batch_tasks table (Phase 2 will reintroduce as workflow)
DROP TABLE batch_tasks;
```

### Status Aggregation Logic

```java
public String computeAggregatedStatus(List<TaskExecution> executions) {
    if (executions.isEmpty()) {
        return "PENDING";
    }
    
    long pendingCount = executions.stream()
        .filter(e -> "PENDING".equals(e.getStatus()))
        .count();
    
    long runningCount = executions.stream()
        .filter(e -> "RUNNING".equals(e.getStatus()) || "PULLED".equals(e.getStatus()))
        .count();
    
    long successCount = executions.stream()
        .filter(e -> "SUCCESS".equals(e.getStatus()))
        .count();
    
    long failedCount = executions.stream()
        .filter(e -> "FAILED".equals(e.getStatus()) || "TIMEOUT".equals(e.getStatus()))
        .count();
    
    // If any are still running or pending, task is in progress
    if (runningCount > 0 || pendingCount > 0) {
        return "IN_PROGRESS";
    }
    
    // All completed
    if (successCount == executions.size()) {
        return "ALL_SUCCESS";
    } else if (failedCount == executions.size()) {
        return "ALL_FAILED";
    } else {
        return "PARTIAL_SUCCESS";
    }
}
```

## Correctness Properties

*A property is a characteristic or behavior that should hold true across all valid executions of a system—essentially, a formal statement about what the system should do. Properties serve as the bridge between human-readable specifications and machine-verifiable correctness guarantees.*

### Property 1: Task Creation with Multiple Agents

*For any* list of agent IDs and task specification, when creating a task, the system should create exactly one Task record and N TaskExecution records (where N equals the number of agent IDs), and all TaskExecution records should have status PENDING and be linked to the created Task.

**Validates: Requirements 1.2, 2.1**

### Property 2: Unique Execution Identifiers

*For any* task created with multiple agents, all TaskExecution records should have unique IDs and all should reference the same parent taskId.

**Validates: Requirements 1.3**

### Property 3: Script Content Stored Once

*For any* task regardless of the number of target agents, the Task table should contain exactly one record with the script content, and no script content should be duplicated in TaskExecution records.

**Validates: Requirements 1.5**

### Property 4: Status Transition Correctness

*For any* TaskExecution record, when transitioning through states (PENDING → PULLED → RUNNING → SUCCESS/FAILED/TIMEOUT), each transition should update the corresponding timestamp field (pulledAt, startedAt, finishedAt) and maintain valid state ordering.

**Validates: Requirements 2.2, 2.3, 2.4**

### Property 5: Aggregated Status Computation

*For any* task with multiple executions, the aggregated status should be computed correctly based on the following rules:
- All SUCCESS → ALL_SUCCESS
- All FAILED/TIMEOUT → ALL_FAILED  
- Mix of SUCCESS and FAILED/TIMEOUT → PARTIAL_SUCCESS
- Any RUNNING/PULLED → IN_PROGRESS
- All PENDING → PENDING

**Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5**

### Property 6: Execution Progress Calculation

*For any* task with N target agents, the execution progress should equal the count of executions with terminal status (SUCCESS, FAILED, TIMEOUT, CANCELLED) divided by N, and should be displayed as "X/N completed".

**Validates: Requirements 3.6**

### Property 7: Log Retrieval Correctness

*For any* TaskExecution record with a logFilePath, retrieving logs for that execution should return the content from that specific log file and not from any other execution's log file.

**Validates: Requirements 6.1**

### Property 8: Selective Restart - Failed Only

*For any* task with mixed execution statuses, when restarting with mode FAILED_ONLY, the system should create new TaskExecution records only for executions with status FAILED or TIMEOUT, and should not create new records for SUCCESS executions.

**Validates: Requirements 7.3**

### Property 9: Restart All Executions

*For any* task, when restarting with mode ALL, the system should create new TaskExecution records for all target agents regardless of their previous execution status.

**Validates: Requirements 7.4**

### Property 10: Execution Number Increment

*For any* task-agent combination, each restart should increment the executionNumber field sequentially (1, 2, 3, ...), and the system should maintain the history of all previous execution attempts.

**Validates: Requirements 7.5, 7.6, 11.5**

### Property 11: Restart Initial Status

*For any* newly created TaskExecution record from a restart operation, the initial status should be PENDING and all timestamp fields (pulledAt, startedAt, finishedAt) should be null.

**Validates: Requirements 7.7**

### Property 12: Individual Execution Cancellation

*For any* specific TaskExecution record, when cancelled, only that execution's status should be updated to CANCELLED and finishedAt should be recorded, while other executions of the same task should remain unchanged.

**Validates: Requirements 8.1, 8.5**

### Property 13: Bulk Task Cancellation

*For any* task, when cancelled, all TaskExecution records with status PENDING, PULLED, or RUNNING should be updated to CANCELLED, while executions with terminal status (SUCCESS, FAILED, TIMEOUT) should remain unchanged.

**Validates: Requirements 8.3**

### Property 14: Cancellation Validation

*For any* TaskExecution record with terminal status (SUCCESS, FAILED, TIMEOUT), attempting to cancel it should be rejected and the status should remain unchanged.

**Validates: Requirements 8.4**

### Property 15: Cascade Deletion

*For any* task, when deleted, all associated TaskExecution records should also be deleted, and querying for those execution records should return empty results.

**Validates: Requirements 11.3**

### Property 16: Agent Task Assignment

*For any* agent pulling tasks, the system should return only TaskExecution records where the agentId matches the requesting agent and the status is PENDING, and should not return executions assigned to other agents.

**Validates: Requirements 15.1**

### Property 17: Task Serialization Round-Trip

*For any* valid Task object, serializing to JSON then deserializing back to an object then serializing again should produce equivalent JSON output, preserving all field values.

**Validates: Requirements 16.1, 16.2, 16.3, 16.4**

## Error Handling

### Input Validation Errors

1. **Empty Agent List**: When creating a task with an empty agent list, return HTTP 400 with error message "至少需要选择一个代理"
2. **Invalid Agent ID**: When creating a task with non-existent agent IDs, return HTTP 404 with error message "代理不存在: {agentId}"
3. **Missing Required Fields**: When task specification is missing required fields (scriptContent, scriptLang, timeoutSec), return HTTP 400 with specific field error messages
4. **Invalid Timeout**: When timeoutSec is negative or zero, return HTTP 400 with error message "超时时间必须大于0"

### State Transition Errors

1. **Invalid Status Transition**: When attempting an invalid status transition (e.g., PENDING → SUCCESS without RUNNING), log warning and reject the transition
2. **Duplicate Pull**: When an agent attempts to pull a task that is already PULLED or RUNNING, return HTTP 409 with error message "任务已被拉取"
3. **Update Non-Existent Execution**: When updating status for non-existent executionId, return HTTP 404 with error message "执行实例不存在"

### Authorization Errors

1. **Agent Mismatch**: When an agent attempts to update a TaskExecution not assigned to it, return HTTP 403 with error message "无权限更新此执行实例"
2. **Unauthorized Cancellation**: When a non-admin user attempts to cancel another user's task, return HTTP 403 with error message "无权限取消此任务"

### Resource Errors

1. **Log File Not Found**: When log file does not exist, return HTTP 404 with error message "日志文件不存在"
2. **Log File Read Error**: When log file cannot be read due to permissions or I/O error, return HTTP 500 with error message "读取日志文件失败"
3. **Database Connection Error**: When database is unavailable, return HTTP 503 with error message "服务暂时不可用，请稍后重试"

### Concurrent Modification Errors

1. **Optimistic Lock Failure**: When concurrent updates conflict, retry the operation up to 3 times, then return HTTP 409 with error message "并发更新冲突，请重试"
2. **Task Already Deleted**: When attempting to operate on a deleted task, return HTTP 404 with error message "任务不存在或已被删除"

### Migration Errors

1. **Duplicate Migration**: When migration script is run multiple times, detect existing TaskExecution records and skip migration with warning log
2. **Foreign Key Violation**: When migration encounters orphaned records, log the taskIds and skip those records, continue with valid records
3. **Rollback Failure**: When rollback script fails, log detailed error and provide manual recovery steps in documentation

## Testing Strategy

### Dual Testing Approach

This feature requires both unit tests and property-based tests to ensure comprehensive coverage:

- **Unit tests**: Verify specific examples, edge cases, and error conditions
- **Property tests**: Verify universal properties across all inputs
- Both approaches are complementary and necessary for complete validation

### Property-Based Testing

We will use **fast-check** (for JavaScript/TypeScript) and **JUnit-Quickcheck** (for Java) as our property-based testing libraries.

Each property test should:
- Run minimum 100 iterations to ensure comprehensive input coverage
- Be tagged with a comment referencing the design property
- Tag format: `// Feature: task-multi-target-support, Property {number}: {property_text}`

### Unit Testing Focus

Unit tests should focus on:
- Specific examples that demonstrate correct behavior (e.g., creating a task with 3 specific agents)
- Edge cases (e.g., empty agent list, timeout values, null fields)
- Error conditions (e.g., invalid agent IDs, unauthorized access)
- Integration points between components (e.g., TaskService → TaskExecutionService)

Avoid writing too many unit tests for scenarios that property tests already cover comprehensively.

### Test Coverage by Component

#### Backend Tests (Java + JUnit + JUnit-Quickcheck)

**TaskService Tests**:
- Property Test: Task creation with multiple agents (Property 1)
- Property Test: Script content stored once (Property 3)
- Property Test: Aggregated status computation (Property 5)
- Property Test: Execution progress calculation (Property 6)
- Property Test: Cascade deletion (Property 15)
- Unit Test: Create task with empty agent list (should fail)
- Unit Test: Create task with invalid agent ID (should fail)
- Unit Test: Create task with missing required fields (should fail)

**TaskExecutionService Tests**:
- Property Test: Status transition correctness (Property 4)
- Property Test: Unique execution identifiers (Property 2)
- Property Test: Execution number increment (Property 10)
- Property Test: Restart initial status (Property 11)
- Unit Test: Update execution with invalid status transition (should fail)
- Unit Test: Agent attempts to update another agent's execution (should fail)

**RestartService Tests**:
- Property Test: Selective restart - failed only (Property 8)
- Property Test: Restart all executions (Property 9)
- Unit Test: Restart task with no failed executions using FAILED_ONLY mode
- Unit Test: Restart task with all successful executions using ALL mode

**CancellationService Tests**:
- Property Test: Individual execution cancellation (Property 12)
- Property Test: Bulk task cancellation (Property 13)
- Property Test: Cancellation validation (Property 14)
- Unit Test: Cancel already completed execution (should fail)
- Unit Test: Cancel execution for non-existent task (should fail)

**LogService Tests**:
- Property Test: Log retrieval correctness (Property 7)
- Unit Test: Retrieve log for non-existent execution (should fail)
- Unit Test: Retrieve log when file does not exist (should return empty)

**AgentTaskPullService Tests**:
- Property Test: Agent task assignment (Property 16)
- Unit Test: Agent pulls task when no pending tasks exist
- Unit Test: Agent pulls task that is already pulled by another agent

**SerializationService Tests**:
- Property Test: Task serialization round-trip (Property 17)
- Unit Test: Serialize task with null fields (should handle gracefully)
- Unit Test: Deserialize invalid JSON (should fail with descriptive error)

#### Frontend Tests (React + Jest + fast-check)

**TaskCreateForm Tests**:
- Unit Test: Multi-select agent component renders correctly
- Unit Test: Form validation prevents submission with empty agent list
- Unit Test: Form submits correct payload with multiple agents

**TaskList Tests**:
- Unit Test: Displays target agent count correctly
- Unit Test: Displays execution progress correctly
- Unit Test: Displays aggregated status with correct visual indicators

**TaskDetailModal Tests**:
- Unit Test: Displays all execution instances
- Unit Test: Filters execution instances by status
- Unit Test: Opens log modal when clicking view log button

**ExecutionLogModal Tests**:
- Unit Test: Displays log content correctly
- Unit Test: Shows appropriate message when log is empty
- Unit Test: Copy to clipboard functionality works

#### Integration Tests

**End-to-End Task Creation Flow**:
1. Create task with 3 agents via API
2. Verify 1 Task record and 3 TaskExecution records in database
3. Verify all executions have status PENDING
4. Verify task appears in task list with correct agent count

**End-to-End Task Execution Flow**:
1. Create task with 2 agents
2. Agent 1 pulls task → verify status PULLED
3. Agent 1 starts execution → verify status RUNNING
4. Agent 1 completes successfully → verify status SUCCESS
5. Agent 2 completes with failure → verify status FAILED
6. Verify task aggregated status is PARTIAL_SUCCESS
7. Verify execution progress is "2/2 completed"

**End-to-End Restart Flow**:
1. Create task with 3 agents
2. Complete 2 successfully, 1 failed
3. Restart with FAILED_ONLY mode
4. Verify only 1 new TaskExecution created
5. Verify executionNumber incremented to 2
6. Verify previous execution history preserved

**Data Migration Test**:
1. Create 10 single-agent tasks in old schema
2. Run migration script
3. Verify 10 Task records and 10 TaskExecution records
4. Verify all data migrated correctly (logs, status, timestamps)
5. Verify foreign key constraints enforced

### Test Data Generation

For property-based tests, we need generators for:

**Task Generator**:
```java
Arbitrary<Task> taskGen = Arbitraries.combine(
    Arbitraries.strings().alpha().ofLength(10), // taskName
    Arbitraries.of("bash", "python", "node"),   // scriptLang
    Arbitraries.strings().ofMinLength(1),       // scriptContent
    Arbitraries.integers().between(1, 3600)     // timeoutSec
).as((name, lang, content, timeout) -> 
    new Task(name, lang, content, timeout)
);
```

**Agent ID List Generator**:
```java
Arbitrary<List<String>> agentIdsGen = 
    Arbitraries.strings().alpha().ofLength(8)
        .list().ofMinSize(1).ofMaxSize(10);
```

**TaskExecution Status Generator**:
```java
Arbitrary<String> statusGen = Arbitraries.of(
    "PENDING", "PULLED", "RUNNING", 
    "SUCCESS", "FAILED", "TIMEOUT", "CANCELLED"
);
```

### Continuous Integration

All tests should run on every commit:
- Unit tests: < 5 minutes total execution time
- Property tests: < 10 minutes total execution time (100 iterations each)
- Integration tests: < 15 minutes total execution time

Minimum coverage requirements:
- Backend: 80% line coverage, 70% branch coverage
- Frontend: 70% line coverage, 60% branch coverage

