ALTER TABLE agent_execution
    ADD COLUMN correction_cycles_used INT NOT NULL DEFAULT 0;
