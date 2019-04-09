import java.util.*;
import java.io.*;

public class Banker {
    public static void main(String[] args) throws IOException {
        // Simulate the cycle process using a static var/ var main and decrementing at every cycle.
        // Use a readyQueue and running process.
        File f = new File("lab3-io/input-12");
        Scanner input = new Scanner(f);
        List<String> tempTaskList = new ArrayList<>();
        List<Task> taskList = new ArrayList<>();
        List<Task> masterTaskList = new ArrayList<>();
        List<Task> terminatedList = new ArrayList<>();
        Set<Integer> abortedTasks = new HashSet<>();

        // Static vars - set total number of tasks, resources, and the max claim each resource has
        String[] firstLine = input.nextLine().split("\\s+");
        int numActiveTasks = Integer.parseInt(firstLine[0]);
        int numResources = Integer.parseInt(firstLine[1]);

        Task.numTasks = numActiveTasks;
        Task.numResources = numResources;
        
        for (int i = 0; i < Task.numResources; i++) {
            int maxClaim = Integer.parseInt(firstLine[i + 2]);
            Task.maxClaims.add(maxClaim);
        }

        // Put all the strings into an arraylist
        // Then keep a number representing each task. While number < numTasks, iterate and add tasks of that number into taskList
        // put it into masterTaskList if initiated, put it into taskList
        
        while (input.hasNext()) {
            // Skip empty lines
            String line = input.nextLine();
            if (line.length() != 0)
                tempTaskList.add(line);
        }

        int tempTaskIndex = 1;
        int cycle = 0;
        Set<Integer> seen = new HashSet<>();
        while (tempTaskIndex <= numActiveTasks) {
            for (String taskString : tempTaskList) {
                String[] taskStringArray = taskString.split("\\s+");
                String type = taskStringArray[0];
                int currentTaskNumber = Integer.parseInt(taskStringArray[1]);
                int delay = Integer.parseInt(taskStringArray[2]);
                int resourceType = Integer.parseInt(taskStringArray[3]);
                int numberQuery = Integer.parseInt(taskStringArray[4]);

                // Skip if not the task we're looking for
                if (currentTaskNumber != tempTaskIndex)
                    continue;

                Task currentTask = new Task();

                currentTask.readyTime = cycle++;
                currentTask.type = type;
                currentTask.taskNumber = currentTaskNumber;
                currentTask.delay = delay;
                currentTask.resourceType = resourceType;
                currentTask.isBlocked = false;
                currentTask.waitingTime = 0;

                switch (type) {
                    case "request":
                        currentTask.numberRequested = numberQuery;
                        break;
                    case "release":
                        currentTask.numberReleased = numberQuery;
                        break;
                }
                if (type.equals("initiate") && !seen.contains(currentTaskNumber)) {
                    masterTaskList.add(currentTask);
                    seen.add(currentTaskNumber);
                }
                    
                taskList.add(currentTask);
            }
            // Increment onto the next task, reset the cycle
            tempTaskIndex++;
            cycle = 0;
        }
        
        // ------------MAIN PROCESS LOOP------------

        Queue<Task> readyQueue = new LinkedList<>(); // Tasks at every given cycle
        LinkedList<Task> blockedQueue = new LinkedList<>(); // For potentially removing deadlocks
        
        List<Integer> delayTimes = new ArrayList<>(); // Tracks each individual delay time
        Set<Task> seenTasks = new HashSet<>(); // Tracks delayed tasks
        List<Boolean> usedThisCycle = new ArrayList<>(); // Tracks if this task has been used this cycle
        List<Boolean> taskIsPending = new ArrayList<>(); // Tracking tasks that are pending
        Queue<Task> pendingBlockedQueue = new LinkedList<>(); // Tasks that weren't able to granted that must be re-added to blocked
        Queue<Task> pendingReadyQueue = new LinkedList<>(); // Tasks that weren't able to run because pending ran instead
        Queue<Task> pendingTerminatedQueue = new LinkedList<>(); // Tasks to be added to terminated list at end

        for (int count = 0; count < numActiveTasks; count++) {
            taskIsPending.add(false);
            usedThisCycle.add(false);
            delayTimes.add(0);
        }
        
        cycle = 0;
        boolean deadlockPossible = false;
        int blockedTasks = 0;

        while (terminatedList.size() + abortedTasks.size() < Task.numTasks) {
            System.out.println("Cycle: " + cycle);

            // Reset usedThisCycle
            for (int count = 0; count < usedThisCycle.size(); count++)
                usedThisCycle.set(count, false);

            // Periodically check for ready tasks and load them
            for (Task currentTask : taskList) {
                if (currentTask.readyTime == cycle && !abortedTasks.contains(currentTask.taskNumber)) {
                    readyQueue.add(currentTask);
                }
            }

            // Values to be released at the end of each given cycle
            Map<Integer, Integer> releaseVals = new HashMap<>();
            List<Integer> masterTaskIndices = new ArrayList<>();

            // First check for pending requests
            while (!blockedQueue.isEmpty()) {
                Task currentTask = blockedQueue.poll();
                int taskIndex = currentTask.taskNumber - 1;
                taskIsPending.set(taskIndex, true);

                // If possible, grant resources and subtract accordingly.
                int claimIndex = currentTask.resourceType - 1;
                        
                if (currentTask.numberRequested <= Task.maxClaims.get(claimIndex)) {
                    System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " + 
                    currentTask.numberRequested + " units of " + currentTask.resourceType + " was granted.");
                    Task.maxClaims.set(claimIndex, Task.maxClaims.get(claimIndex) - currentTask.numberRequested);
                    blockedTasks--;
                }
                else {
                    System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " +
                    currentTask.numberRequested + " units of " + currentTask.resourceType + " was unable to be granted.");
                    // Add the task to pending blocked queue, will be re-queued at the end of this cycle.
                    pendingBlockedQueue.add(currentTask);
                    currentTask.isBlocked = true;
                }
            }

            // Deadlock Check
            if (blockedTasks == numActiveTasks)
                deadlockPossible = true;
            else {
                deadlockPossible = false;
            }

            while (!readyQueue.isEmpty()) {    
                // If task delay exists, don't poll it
                Task currPeekTask = readyQueue.peek();
                int peekTime = readyQueue.peek().taskNumber - 1;
                if (!seenTasks.contains(currPeekTask)) {
                    delayTimes.set(peekTime, currPeekTask.delay);
                    seenTasks.add(currPeekTask);
                }

                if (delayTimes.get(peekTime) > 0) {
                    System.out.println("Delay time is " + delayTimes.get(peekTime));
                    delayTimes.set(peekTime, delayTimes.get(peekTime) - 1);
                    continue;
                }

                Task currentTask = readyQueue.poll();
                int taskIndex = currentTask.taskNumber - 1;
                Task masterTask = masterTaskList.get(taskIndex);

                // Skip if aborted
                if (abortedTasks.contains(currentTask.taskNumber))
                    continue;

                // Check if it's been used this cycle
                // Skip if it was handled by pending, set tasks as non-pending at end
                if (taskIsPending.get(taskIndex) || usedThisCycle.get(taskIndex)) {
                    pendingReadyQueue.add(currentTask);
                    continue;
                }

                switch (currentTask.type) {
                    case "initiate":
                        System.out.println("Task " + currentTask.taskNumber + " initiated, resource type "
                        + currentTask.resourceType);
                        // masterTaskList.add(currentTask);
                        break;
                    case "request":
                        // If possible, grant resources and subtract accordingly.
                        int claimIndex = currentTask.resourceType - 1;
                        if (currentTask.numberRequested <= Task.maxClaims.get(claimIndex)) {
                            System.out.println("Task " + currentTask.taskNumber + "'s request for " + 
                            currentTask.numberRequested + " units of " + currentTask.resourceType + " was granted.");
                            // Update resources granted in master list
                            Task.maxClaims.set(claimIndex, Task.maxClaims.get(claimIndex) - currentTask.numberRequested);
                            masterTask.numberGranted += currentTask.numberRequested;

                        }
                        // Couldn't grant resources
                        else {
                            System.out.println("Task " + currentTask.taskNumber + "'s request for " +
                            currentTask.numberRequested + " units of " + currentTask.resourceType + " was unable to be granted.");
                            blockedTasks++;
                            pendingBlockedQueue.add(currentTask);
                        }
                        break;
                    case "release":
                        // These need to be released at the END of the cycle
                        int releaseIndex = currentTask.resourceType - 1;
                        System.out.println("Task " + currentTask.taskNumber + " releases " +
                        currentTask.numberReleased + " units of " + "resource " + currentTask.resourceType);
                        // releaseIndex and numberReleased
                        if (!releaseVals.containsKey(releaseIndex)) {
                            releaseVals.put(releaseIndex, currentTask.numberReleased);
                        }

                        masterTaskIndices.add(taskIndex);
                        break;
                    case "terminate":
                        System.out.println("Task " + currentTask.taskNumber + " terminated at " + (cycle + 1) + ".");
                        pendingTerminatedQueue.add(currentTask);
                        break;
                } // end switch
                usedThisCycle.set(currentTask.taskNumber - 1, true);

                if (blockedTasks == numActiveTasks) {
                    deadlockPossible = true;
                } else {
                    deadlockPossible = false;
                }

                if (deadlockPossible) {
                    System.out.println("Deadlock is possible now.");
                    break;
                }
                    

            } // end while !readyQueue empty
            
            // Free resources HERE
            int i = 0;
            for (Integer releaseIndex : releaseVals.keySet()) {
                Task masterTask = masterTaskList.get(i++);
                int numberReleased = releaseVals.get(releaseIndex);
                Task.maxClaims.set(releaseIndex, Task.maxClaims.get(releaseIndex) + numberReleased);
                masterTask.numberGranted -= numberReleased;
                if (masterTask.numberGranted < 0)
                    masterTask.numberGranted = 0;
            }

            // Pending blocked tasks get readded to blockedQueue
            while (!pendingBlockedQueue.isEmpty()) {
                Task pendingBlockedTask = pendingBlockedQueue.poll();
                int taskNumber = pendingBlockedTask.taskNumber;
                System.out.println("Adding " + pendingBlockedTask.taskNumber + " to blocked.");
                pendingBlockedTask.isBlocked = true;
                pendingBlockedTask.waitingTime++;
                masterTaskList.get(taskNumber - 1).waitingTime++;
                blockedQueue.add(pendingBlockedTask);
            }

            // Pending ready tasks get readded to readyQueue
            while (!pendingReadyQueue.isEmpty()) {
                Task pendingReadyTask = pendingReadyQueue.poll();
                int taskNumber = pendingReadyTask.taskNumber - 1;
                readyQueue.add(pendingReadyTask);
                taskIsPending.set(taskNumber, false);
            }

            // Pending terminated tasks get added to terminatedList
            while (!pendingTerminatedQueue.isEmpty()) {
                Task pendingTerminatedTask = pendingTerminatedQueue.poll();
                int terminatedTime = cycle + 1;
                pendingTerminatedTask.terminatedTime = terminatedTime;
                masterTaskList.get(pendingTerminatedTask.taskNumber - 1).terminatedTime = terminatedTime;
                terminatedList.add(pendingTerminatedTask); 
                numActiveTasks--;
            }

            // Free and abort blocked tasks in order of 1, 2, 3 ... n until resources are available again
            int number = 1;
            int index = 1;

            while (deadlockPossible) {
                System.out.println("Index: " + number);
                Task currentTask = null;
                
                for (Task blockedTask : blockedQueue) {
                    if (blockedTask.taskNumber == number) {
                        currentTask = blockedTask;
                        break;
                    }
                }
                number++;

                if (currentTask == null || !currentTask.isBlocked) {
                    System.out.println("Skipping " + index);
                    index++;
                    continue;
                }
                
                blockedQueue.remove(currentTask);
                System.out.println("Removed " + currentTask.taskNumber);

                int taskIndex = currentTask.taskNumber - 1;
                Task masterTask = masterTaskList.get(taskIndex);

                System.out.println("Task " + currentTask.taskNumber + " was aborted. " + masterTask.numberGranted + " freed.");
                if (masterTask.numberGranted != 0)
                    blockedTasks--;
                
                int releaseIndex = currentTask.resourceType - 1;
                Task.maxClaims.set(releaseIndex, Task.maxClaims.get(releaseIndex) + masterTask.numberGranted);
                masterTask.numberGranted = 0;
                abortedTasks.add(currentTask.taskNumber);
                System.out.println("ABORTED: " + abortedTasks);
                // Need to check if the next one can be properly given
                Task nextTask = null;
                for (Task blockedTask : blockedQueue) {
                    if (blockedTask.taskNumber == number) {
                        nextTask = blockedTask;
                        break;
                    }
                }

                if (nextTask == null)
                    continue;

                if (nextTask.numberRequested <= Task.maxClaims.get(releaseIndex)) {
                    System.out.println("deadlock has ended.");
                    deadlockPossible = false;
                }

            }

            cycle++;
        } // end outer while

        printInfo(masterTaskList, abortedTasks);

        input.close();
    } // End main

    public static void printInfo(List<Task> masterTaskList, Set<Integer> abortedTasks) {
        System.out.print("------------\n");
        int numTasks = masterTaskList.size();
        
        for (int i = 1; i <= numTasks; i++) {
            System.out.print("Task " + i + "\t");
            if (abortedTasks.contains(i)){
                System.out.print("Aborted \n");
            }
            else {
                Task currentTask = masterTaskList.get(i - 1);
                int terminatedTime = currentTask.terminatedTime;
                int waitingTime = currentTask.waitingTime;

                System.out.print("Terminated at: " + terminatedTime + "\t");
                System.out.print("Waiting time: " + waitingTime + "\t");
                System.out.print("Waiting time %: " + ((waitingTime/(double)(terminatedTime)) * 100) + "%\n");
            }   
        }
    }
}
