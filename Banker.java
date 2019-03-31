import java.util.*;
import java.io.*;

public class Banker {
    public static void main(String[] args) throws IOException {
        // Simulate the cycle process using a static var/ var main and decrementing at every cycle.
        // Use a readyQueue and running process.
        File f = new File("lab3-io/input-03");
        Scanner input = new Scanner(f);
        List<Task> taskList = new ArrayList<>();
        List<Task> terminatedList = new ArrayList<>();
        Set<Integer> abortedTasks = new HashSet<>();

        String[] firstLine = input.nextLine().split("\\s+");
        int numTasks = Integer.parseInt(firstLine[0]);
        int numResources = Integer.parseInt(firstLine[1]);
        int maxClaim = Integer.parseInt(firstLine[2]);

        // Static vars - set total number of tasks, resources, and the max claim each resource has

        Task.numTasks = numTasks;
        Task.numResources = numResources;

        for (int i = 0; i < Task.numResources; i++) 
            Task.maxClaims.add(maxClaim);
        
        int cycle = 0;

        while (input.hasNext()) {
            String line = input.nextLine();
            String[] lineArray = line.split("\\s+");
            Task currentTask = new Task();
            String type = lineArray[0];
            // Reset cycle time for each process
            if (type.equals("initiate")) {
                cycle = 0;
            }
            currentTask.readyTime = cycle++;

            int taskNumber = Integer.parseInt(lineArray[1]);
            int delay = Integer.parseInt(lineArray[2]);
            int resourceType = Integer.parseInt(lineArray[3]);
            int numberQuery = Integer.parseInt(lineArray[4]);
            
            currentTask.type = type;
            currentTask.taskNumber = taskNumber;
            currentTask.delay = delay;
            currentTask.resourceType = resourceType;

            switch (type) {
                case "request":
                    currentTask.numberRequested = numberQuery;
                    break;
                case "release":
                    currentTask.numberReleased = numberQuery;
                    break;
            }

            taskList.add(currentTask);
        }
        
        // ------------MAIN PROCESS LOOP------------

        Queue<Task> readyQueue = new LinkedList<>();
        Queue<Task> blockedQueue = new LinkedList<>(); // For potentially removing deadlocks
        cycle = 0;
        boolean deadlockPossible = false;
        while (terminatedList.size() + abortedTasks.size() < Task.numTasks) {
            System.out.println("Cycle: " + cycle);
            
            // First check for pending requests
            boolean pendingRequests = false;
            while (!blockedQueue.isEmpty()) {
                pendingRequests = true;
                Task currentTask = blockedQueue.poll();
                // If possible, grant resources and subtract accordingly.
                int claimIndex = currentTask.resourceType - 1;
                        
                if (currentTask.numberRequested <= Task.maxClaims.get(claimIndex)) {
                    System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " + 
                    currentTask.numberRequested + " was granted.");
                    Task.maxClaims.set(claimIndex, Task.maxClaims.get(claimIndex) - currentTask.numberRequested);
                }
                else {
                    System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " +
                    currentTask.numberRequested + " was unable to be granted.");
                }
            }

            if (pendingRequests) {
                cycle++;
                continue;
            }
            
            // Periodically check for ready tasks
            for (Task currentTask : taskList) {
                if (currentTask.readyTime == cycle && !abortedTasks.contains(currentTask.taskNumber))
                    readyQueue.add(currentTask);
            }

            while (!readyQueue.isEmpty()) {
                Task currentTask = readyQueue.poll();
                
                switch (currentTask.type) {
                    case "initiate":
                        System.out.println("Task " + currentTask.taskNumber + " initiated.");
                        break;
                    case "request":
                        // If possible, grant resources and subtract accordingly.
                        int claimIndex = currentTask.resourceType - 1;
                        if (currentTask.numberRequested <= Task.maxClaims.get(claimIndex)) {
                            System.out.println("Task " + currentTask.taskNumber + "'s request for " + 
                            currentTask.numberRequested + " was granted.");
                            Task.maxClaims.set(claimIndex, Task.maxClaims.get(claimIndex) - currentTask.numberRequested);
                            deadlockPossible = false;
                        }
                        // Couldn't grant resources
                        else {
                            System.out.println("Task " + currentTask.taskNumber + "'s request for " +
                            currentTask.numberRequested + " was unable to be granted.");
                            // Deadlock is now possible
                            deadlockPossible = true;
                            blockedQueue.add(currentTask);
                        }
                        break;
                    case "release":
                        int releaseIndex = currentTask.resourceType - 1;
                        Task.maxClaims.set(releaseIndex, Task.maxClaims.get(releaseIndex) + currentTask.numberReleased);
                        System.out.println("Task " + currentTask.taskNumber + " releases " +
                        currentTask.numberReleased + " units.");
                        break;
                    case "terminate":
                        System.out.println("Task " + currentTask.taskNumber + " terminated at " + cycle + ".");
                        terminatedList.add(currentTask);
                        break;
                } // end switch

            } // end while !readyQueue empty

            // Free and abort the first blocked task
            while (deadlockPossible && !blockedQueue.isEmpty()) {
                Task currentTask = blockedQueue.poll();
                
                int releaseIndex = currentTask.resourceType - 1;
                Task.maxClaims.set(releaseIndex, Task.maxClaims.get(releaseIndex) + currentTask.numberRequested);
                abortedTasks.add(currentTask.taskNumber);

                if (!blockedQueue.isEmpty() && blockedQueue.peek().numberRequested <= Task.maxClaims.get(releaseIndex)) {
                    break;
                }
            }

            cycle++;
        } // end outer while

        input.close();
    }

}
