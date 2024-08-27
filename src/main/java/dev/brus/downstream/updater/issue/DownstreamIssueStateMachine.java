package dev.brus.downstream.updater.issue;

public interface DownstreamIssueStateMachine extends IssueStateMachine {

   String getIssueStateToDo();

   String getIssueStateDevComplete();
}
