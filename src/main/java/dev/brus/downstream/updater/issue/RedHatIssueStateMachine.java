package dev.brus.downstream.updater.issue;

public class RedHatIssueStateMachine implements DownstreamIssueStateMachine {

   private static final String ISSUE_STATE_TODO = "To Do";
   private static final String ISSUE_STATE_DEV_COMPLETE = "Dev Complete";

   @Override
   public int getStateIndex(String state) {
      switch (state) {
         case "New":
            return 0;
         case "Backlog":
            return 1;
         case "Tasking and Estimation":
            return 2;
         case "To Do":
            return 3;
         case "In Progress":
            return 4;
         case "Code Review":
            return 5;
         case "Dev Complete":
            return 6;
         case "Testing":
            return 7;
         case "Review":
            return 8;
         case "Verified":
            return 9;
         case "Release Pending":
            return 10;
         case "Resolved":
            return 11;
         case "Closed":
            return 12;
         default:
            throw new IllegalStateException("Invalid state: " + state);
      }
   }

   @Override
   public String getNextState(String fromState, String toState) {
      return toState;
   }

   @Override
   public String getIssueStateToDo() {
      return ISSUE_STATE_TODO;
   }

   @Override
   public String getIssueStateDevComplete() {
      return ISSUE_STATE_DEV_COMPLETE;
   }
}
