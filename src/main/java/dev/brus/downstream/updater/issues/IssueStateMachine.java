package dev.brus.downstream.updater.issues;

public interface IssueStateMachine {

   String getNextState(String fromState, String toState);

   int getStateIndex(String state);

}
