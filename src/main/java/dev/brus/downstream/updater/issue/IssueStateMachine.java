package dev.brus.downstream.updater.issue;

public interface IssueStateMachine {

   String getNextState(String fromState, String toState);

   int getStateIndex(String state);

}
