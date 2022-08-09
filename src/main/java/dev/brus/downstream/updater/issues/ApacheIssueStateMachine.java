package dev.brus.downstream.updater.issues;

public class ApacheIssueStateMachine implements IssueStateMachine {

   @Override
   public int getStateIndex(String state) {
      switch (state) {
         case "Open":
            return 0;
         case "Reopened":
            return 1;
         case "In Progress":
            return 2;
         case "Resolved":
            return 3;
         case "Closed":
            return 4;
         default:
            throw new IllegalStateException("Invalid state: " + state);
      }
   }

   @Override
   public String getNextState(String fromState, String toState) {
      throw new UnsupportedOperationException();
   }
}
