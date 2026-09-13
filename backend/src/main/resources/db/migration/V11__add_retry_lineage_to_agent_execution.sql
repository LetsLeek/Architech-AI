-- AIW-149: retry lineage between AgentExecutions. Nullable - most executions are not a retry
-- of anything. A retry is always a new AgentExecution row (see AgentExecution's own class doc,
-- AIW-38's original "never a mutation" rule); this column just records which prior execution (if
-- any) it retries and never gets updated after insert, matching every other column here.
ALTER TABLE agent_execution
    ADD COLUMN retry_of_execution_id UUID REFERENCES agent_execution (id),
    ADD COLUMN retry_reason_code VARCHAR(50);

CREATE INDEX idx_agent_execution_retry_of_execution_id ON agent_execution (retry_of_execution_id);
