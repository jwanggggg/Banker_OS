import java.util.*;
import java.io.*;

public class BankerFinal {
    public static void main(String[] args) throws IOException {
        // Simulate the cycle process using a static var/ var main and decrementing at every cycle.
        // Use a readyQueue and running process.
        File f = new File("lab3-io/input-11");
        Scanner input = new Scanner(f);
        List<String> tempTaskList = new ArrayList<>();
        
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
        input.close();
        
        // FIFO(tempTaskList, numActiveTasks);
        banker(tempTaskList, numActiveTasks);
    } // End main

    public static void banker(List<String> tempTaskList, int numActiveTasks) {
        List<Task> taskList = new ArrayList<>();
        List<Task> masterTaskList = new ArrayList<>();
        List<Task> terminatedList = new ArrayList<>();
        Set<Integer> abortedTasks = new HashSet<>();
        List<Integer> taskClaims = new ArrayList<>();

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
                    // Add the claim for checking later
                    case "initiate":
                        taskClaims.add(numberQuery);
                        currentTask.numberNeeded = numberQuery; // Number needed will decrease as resources are granted.
                        break;
                    case "request":
                        currentTask.numberRequested = numberQuery;
                        break;
                    case "release":
                        currentTask.numberReleased = numberQuery;
                        break;
                }
                if (type.equals("initiate") && !seen.contains(currentTaskNumber)) {
                    currentTask.resourcesGranted = new ArrayList<>();
                    for (int i = 0; i < Task.numTasks; i++)
                        currentTask.resourcesGranted.add(0);
                    masterTaskList.add(currentTask);
                    seen.add(currentTaskNumber);
                }
                    
                taskList.add(currentTask);
            }
            // Increment onto the next task, reset the cycle
            tempTaskIndex++;
            cycle = 0;
        }

        // ------------MAIN BANKER LOOP------------

        List<LinkedList<Task>> readyQueue = new LinkedList<>(); // For processes each cycle
        LinkedList<Task> blockedQueue = new LinkedList<>(); // Blocked processes
        List<Integer> resources = Task.maxClaims; // Number of each resource left
        List<List<Integer>> resourcesNeeded = new ArrayList<>(); // Number of resource each task needs.

        List<Integer> delayTimes = new ArrayList<>(); // Tracks each individual delay time
        Set<Task> seenTasks = new HashSet<>(); // Tracks delayed tasks
        List<Boolean> usedThisCycle = new ArrayList<>(); // Tracks if this task has been used this cycle
        List<Boolean> taskIsPending = new ArrayList<>(); // Tracking tasks that are pending
        Queue<Task> pendingBlockedQueue = new LinkedList<>(); // Tasks that weren't able to granted that must be re-added to blocked
        Queue<Task> pendingReadyQueue = new LinkedList<>(); // Tasks that weren't able to run because pending ran instead
        Queue<Task> pendingReleaseQueue = new LinkedList<>();
        Queue<Task> pendingTerminatedQueue = new LinkedList<>(); // Tasks to be added to terminated list at end

        for (int count = 0; count < numActiveTasks; count++) {
            readyQueue.add(new LinkedList<Task>());
            taskIsPending.add(false);
            List<Integer> list = new ArrayList<>();
            for (int i = 0; i < Task.numTasks; i++)
                list.add(0);
            resourcesNeeded.add(list);        
            delayTimes.add(0);
        }

        cycle = 0;

        while (terminatedList.size() + abortedTasks.size() < Task.numTasks) {
            System.out.println("Cycle: " + cycle);
            
            // Periodically check for ready tasks and load them
            for (int i = 0; i < taskList.size(); i++) {
                Task currentTask = taskList.get(i);
                if (currentTask.readyTime == cycle && !abortedTasks.contains(currentTask.taskNumber)) {
                    readyQueue.get(currentTask.taskNumber - 1).add(currentTask);
                }

                // peek next and add if it's "termination"

                if (i + 1 < taskList.size()) {
                    Task nextTask = taskList.get(i + 1);
                    if (nextTask.type.equals("terminate") && nextTask.readyTime == cycle + 1) {
                        readyQueue.get(nextTask.taskNumber - 1).add(nextTask);
                        i++;
                    }       
                }

            }

            // BLOCKED Tasks
            while (!blockedQueue.isEmpty()) {
                Task currentTask = blockedQueue.poll();

                int taskIndex = currentTask.taskNumber - 1;
                Task masterTask = masterTaskList.get(taskIndex);
                taskIsPending.set(taskIndex, true);

                // If possible, grant resources and subtract accordingly.
                int resourceIndex = currentTask.resourceType - 1;
                int resourceAvailable = resources.get(resourceIndex);
                int numberRequested = currentTask.numberRequested;
                List<Integer> resourceNeedsList = resourcesNeeded.get(resourceIndex);
                int numberNeeded = resourceNeedsList.get(taskIndex);
                boolean isSafe = false;

                // For more than one resource
                if (resources.size() > 1) {
                    if (resourceAvailable >= numberNeeded) {
                        // First check has passed. Now check for safety:
                        // if remaining resources can satisfy any request after claim has been subtracted.
                        resources.set(resourceIndex, resourceAvailable - numberRequested);
                        currentTask.numberGranted += numberNeeded;
                        currentTask.numberNeeded -= numberNeeded;
                        resourceNeedsList.set(taskIndex, resourceAvailable - numberRequested);
                        // Check each needsList
                        
                        System.out.println("needsList: " + resourcesNeeded);
                        System.out.println("banker: " + resources);

                        int i = 0;

                        for (List<Integer> needsList : resourcesNeeded) {
                            int bankerNum = resources.get(i);
                            boolean listIsSafe = true;
                            for (int j = 0; j < needsList.size(); j++) {
                                int needsListNum = needsList.get(j);

                                if (bankerNum - needsListNum < 0) {
                                    listIsSafe = false;
                                    break;
                                }
                            }
                            // Found at least one safe route. We can break.
                            if (listIsSafe) {
                                isSafe = true;
                                System.out.println("isSafe: " + isSafe);
                                break;
                            }

                            i++;
                        }

                        // If it's not safe, refund the resources
                        if (!isSafe) {
                            resources.set(resourceIndex, resourceAvailable);
                            currentTask.numberGranted -= numberNeeded;
                            currentTask.numberNeeded += numberNeeded;
                            resourceNeedsList.set(taskIndex, resourceAvailable);
                        } 
    
                        // If it is safe, grant the resources. We're good to go.
                        else {
                            masterTask.resourcesGranted.set(taskIndex, masterTask.resourcesGranted.get(taskIndex) + 
                            numberRequested);
                            
                            System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " + 
                            numberRequested + " units of " + currentTask.resourceType + " was granted.");
                        }
    
                    }
                } 
                
                // Only 1 resource
                else {
                    if (resourceAvailable >= numberNeeded) {
                        // Grant the request, decrement from resources.
                        System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " + 
                        numberRequested + " units of " + currentTask.resourceType + " was granted.");
                        currentTask.numberGranted += numberRequested;
                        currentTask.numberNeeded -= numberRequested;
                        resources.set(resourceIndex, resourceAvailable - numberRequested);
                        resourceNeedsList.set(taskIndex, resourceAvailable - numberRequested);
                    }
                    else {
                        // Add the task to pending blocked queue, will be re-queued at the end of this cycle.
                        System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " +
                        numberRequested + " units of " + currentTask.resourceType + " was not granted.");
                        pendingBlockedQueue.add(currentTask);
                        currentTask.isBlocked = true;
                    }
                }

                
                if (resources.size() > 1 && (resourceAvailable < numberNeeded || !isSafe)) {
                    // Add the task to pending blocked queue, will be re-queued at the end of this cycle.
                    System.out.println("Task " + currentTask.taskNumber + "'s PENDING request for " +
                    numberRequested + " units of " + currentTask.resourceType + " was not granted.");
                    pendingBlockedQueue.add(currentTask);
                    currentTask.isBlocked = true;
                }
            }

            // Now check for ready queue
            for (int i = 0; i < readyQueue.size(); i++) {
                Queue<Task> currentQueue = readyQueue.get(i);
                // Skip if empty
                if (currentQueue.isEmpty())
                    continue;

                Task peekTask = currentQueue.peek();
                
                // Skip if pending was checked
                if (taskIsPending.get(peekTask.taskNumber - 1)) {
                    pendingReadyQueue.add(currentQueue.poll());
                    continue;
                }

                int resourceType = peekTask.resourceType;
                int taskNumber = peekTask.taskNumber;
                List<Integer> resourceNeedsList = resourceType == 0 ? null : resourcesNeeded.get(resourceType - 1);
                int resourceAvailable = resourceType == 0 ? -1 : resources.get(resourceType - 1); // Skip this if taskType terminated
                Task masterTask = masterTaskList.get(peekTask.taskNumber - 1);

                // Delay checking

                if (!seenTasks.contains(peekTask)) {
                    delayTimes.set(taskNumber - 1, peekTask.delay);
                    seenTasks.add(peekTask);
                }

                if (delayTimes.get(taskNumber - 1) > 0) {
                    System.out.println("Task " + taskNumber + " delayed, time remaining:" + delayTimes.get(taskNumber - 1));
                    delayTimes.set(taskNumber - 1, delayTimes.get(taskNumber - 1) - 1);
                    System.out.println("delay time left: " + delayTimes.get(taskNumber - 1));
                    if (delayTimes.get(taskNumber - 1) == 0 && !currentQueue.isEmpty()
                    && currentQueue.peek().type.equals("terminate")) {
                        pendingTerminatedQueue.add(currentQueue.poll());
                    }
                        
                    continue;
                }

                currentQueue.poll();

                switch (peekTask.type) {
                    // On initiate, set # of resources needed for each task. Add to terminated claim is too big.
                    case "initiate":
                        if (peekTask.numberNeeded > resources.get(resourceType - 1)) {
                            System.out.println("Task " + (i + 1) + " is aborted (claim exceeds total in system)");
                            abortedTasks.add(peekTask.taskNumber);
                        } else {
                            resourceNeedsList.set(taskNumber - 1, peekTask.numberNeeded);
                            System.out.println("Task " + (i + 1) + " has been initiated, resource " + resourceType);
                        }
                        break;
                    case "request":
                        // Add to blocked if the available resource is less than what the given task needs. 
                        int taskClaim = taskClaims.get(i);
                        int numberRequested = peekTask.numberRequested;
                        int numberNeeded = resourceNeedsList.get(taskNumber - 1);

                        // Yes, complete the request
                        if (resourceAvailable >= numberNeeded) {
                            // Check if abortion necessary
                            if (numberRequested > numberNeeded) {
                                // TODO: add to aborted, release the resources.
                                System.out.println("Task " + taskNumber + "'s request exceeds its claim. Aborting.");
                                abortedTasks.add(taskNumber);
                                masterTask.numberReleased = masterTask.numberGranted;
                                pendingReleaseQueue.add(masterTask);
                            } else {
                                // Add the number requested to the task. Decrement from resources.
                                System.out.println("Task " + (i + 1) + " completes its request for " + numberRequested + 
                                " units of " + resourceType);
                                peekTask.numberGranted += numberRequested;
                                peekTask.numberNeeded -= numberRequested;
                                resourceNeedsList.set(taskNumber - 1, resourceNeedsList.get(taskNumber - 1) - numberRequested);
                                resources.set(resourceType - 1, resources.get(resourceType - 1) - numberRequested);

                                masterTask.numberGranted += numberRequested;
                                // Set the master task allocations as well
                                masterTask.resourcesGranted.set(taskNumber - 1, 
                                masterTask.resourcesGranted.get(taskNumber - 1) + numberRequested);
                            }
                        } 
                        
                        // Add to blocked
                        else {
                            System.out.println("Task " + (i + 1) + "'s request for " + numberRequested + 
                            " units of " + resourceType + " cannot be granted");
                            pendingBlockedQueue.add(peekTask);
                        }
                        break;
                    case "release":
                        // Subtract numberReleased from numberGranted. Add numberReleased to resource.
                        pendingReleaseQueue.add(peekTask);
                        System.out.println("Task " + (i + 1) + " releases " + peekTask.numberReleased + " units of " + resourceType);
                        break;
                    case "terminate":
                        if (delayTimes.get(peekTask.taskNumber - 1) <= 0) {
                            pendingTerminatedQueue.add(peekTask);
                        }
                        
                        break;
                }
                
                // Quick, peek and check if the next is terminated and has delay 0, and add if so
                if (!currentQueue.isEmpty()) {
                    Task nextPeekTask = currentQueue.peek();
                    if (nextPeekTask.type.equals("terminate")) {
                        if (nextPeekTask.delay <= 0)
                            pendingTerminatedQueue.add(currentQueue.poll());
                    }
                        
                }

            }

            
            // Releases occur after the cycle has finished.

            while (!pendingReleaseQueue.isEmpty()) {
                Task pendingReleaseTask = pendingReleaseQueue.poll();
                int resourceType = pendingReleaseTask.resourceType;
                int taskNumber = pendingReleaseTask.taskNumber;
                int numberReleased = pendingReleaseTask.numberReleased;
                Task masterTask = masterTaskList.get(taskNumber - 1);

                pendingReleaseTask.numberGranted -= numberReleased;
                pendingReleaseTask.numberNeeded += numberReleased;
 
                List<Integer> resourceNeedsList  = resourcesNeeded.get(resourceType - 1);

                resources.set(resourceType - 1, resources.get(resourceType - 1) + numberReleased);
                resourceNeedsList.set(taskNumber - 1, resourceNeedsList.get(taskNumber - 1) + numberReleased);
                // Set master resource allocations
                masterTask.resourcesGranted.set(taskNumber - 1, masterTask.resourcesGranted.get(taskNumber - 1) - numberReleased);
            }

            // Pending ready tasks get readded to readyQueue
            while (!pendingReadyQueue.isEmpty()) {
                Task pendingReadyTask = pendingReadyQueue.poll();
                int taskNumber = pendingReadyTask.taskNumber - 1;
                readyQueue.get(taskNumber).addFirst(pendingReadyTask);
                taskIsPending.set(taskNumber, false);
            }

            // Pending blocked tasks get readded to blockedQueue
            while (!pendingBlockedQueue.isEmpty()) {
                Task pendingBlockedTask = pendingBlockedQueue.poll();
                pendingBlockedTask.isBlocked = true;
                pendingBlockedTask.waitingTime++;
                masterTaskList.get(pendingBlockedTask.taskNumber - 1).waitingTime++;
                blockedQueue.add(pendingBlockedTask);
            }

            // Pending terminated tasks get freed
            while (!pendingTerminatedQueue.isEmpty()) {
                Task pendingTerminatedTask = pendingTerminatedQueue.poll();
                if (terminatedList.contains(pendingTerminatedTask))
                    continue;
                int resourceType = pendingTerminatedTask.resourceType;
                int taskNum = pendingTerminatedTask.taskNumber;
                Task masterTask = masterTaskList.get(taskNum - 1);
                int terminatedTime = cycle + 1;
                terminatedList.add(pendingTerminatedTask); 
                masterTaskList.get(taskNum - 1).terminatedTime = terminatedTime;
                System.out.println("Task " + pendingTerminatedTask.taskNumber + " terminates.");
            }

            // System.out.println("BANKER: " + resources);
            // System.out.println("Needed: " + resourcesNeeded);

            // if (cycle == 10)
            //     System.exit(0);

            cycle++;
        }

        printInfo(masterTaskList, abortedTasks, "BANKERS");
    }









    // --------------------------------------------------------------------------------

    public static void FIFO(List<String> tempTaskList, int numActiveTasks) {
        List<Task> taskList = new ArrayList<>();
        List<Task> masterTaskList = new ArrayList<>();
        List<Task> terminatedList = new ArrayList<>();
        Set<Integer> abortedTasks = new HashSet<>();

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
        
        // ------------MAIN FIFO LOOP------------

        List<LinkedList<Task>> readyQueue = new LinkedList<>(); // Tasks at every given cycle
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
            readyQueue.add(new LinkedList<>());
        }
        
        cycle = 0;
        boolean deadlockPossible = false;
        int blockedTasks = 0;

        while (terminatedList.size() + abortedTasks.size() < Task.numTasks) {
            // Reset usedThisCycle
            for (int count = 0; count < usedThisCycle.size(); count++)
                usedThisCycle.set(count, false);

            // Periodically check for ready tasks and load them
            for (Task currentTask : taskList) {
                if (currentTask.readyTime == cycle && !abortedTasks.contains(currentTask.taskNumber)) {
                    readyQueue.get(currentTask.taskNumber - 1).add(currentTask);
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
                    Task.maxClaims.set(claimIndex, Task.maxClaims.get(claimIndex) - currentTask.numberRequested);
                    blockedTasks--;
                }
                else {
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

            for (int i = 0; i < readyQueue.size(); i++) {    
                // If task delay exists, don't poll it
                Queue<Task> currentQueue = readyQueue.get(i);
                if (currentQueue.isEmpty())
                    continue;

                Task currPeekTask = currentQueue.peek();
                int peekNumber = currentQueue.peek().taskNumber - 1;
    
                if (!seenTasks.contains(currPeekTask)) {
                    delayTimes.set(peekNumber, currPeekTask.delay);
                    seenTasks.add(currPeekTask);
                }

                if (delayTimes.get(peekNumber) > 0) {
                    delayTimes.set(peekNumber, delayTimes.get(peekNumber) - 1);
                    continue;
                }

                Task currentTask = currentQueue.poll();
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
                        break;
                    case "request":
                        // If possible, grant resources and subtract accordingly.
                        int claimIndex = currentTask.resourceType - 1;
                        if (currentTask.numberRequested <= Task.maxClaims.get(claimIndex)) {
                            // Update resources granted in master list
                            Task.maxClaims.set(claimIndex, Task.maxClaims.get(claimIndex) - currentTask.numberRequested);
                            masterTask.numberGranted += currentTask.numberRequested;

                        }
                        // Couldn't grant resources
                        else {
                            blockedTasks++;
                            pendingBlockedQueue.add(currentTask);
                        }
                        break;
                    case "release":
                        // These need to be released at the END of the cycle
                        int releaseIndex = currentTask.resourceType - 1;
                        // releaseIndex and numberReleased
                        if (!releaseVals.containsKey(releaseIndex)) {
                            releaseVals.put(releaseIndex, currentTask.numberReleased);
                        }
                        masterTaskIndices.add(taskIndex);
                        break;
                    case "terminate":
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
                pendingBlockedTask.isBlocked = true;
                pendingBlockedTask.waitingTime++;
                masterTaskList.get(taskNumber - 1).waitingTime++;
                blockedQueue.add(pendingBlockedTask);
            }

            // Pending ready tasks get readded to readyQueue
            while (!pendingReadyQueue.isEmpty()) {
                Task pendingReadyTask = pendingReadyQueue.poll();
                int taskNumber = pendingReadyTask.taskNumber - 1;
                readyQueue.get(taskNumber).addFirst(pendingReadyTask);
                taskIsPending.set(taskNumber, false);
            }

            // Pending terminated tasks get added to terminatedList
            while (!pendingTerminatedQueue.isEmpty()) {
                Task pendingTerminatedTask = pendingTerminatedQueue.poll();
                int terminatedTime = cycle;
                pendingTerminatedTask.terminatedTime = terminatedTime;
                masterTaskList.get(pendingTerminatedTask.taskNumber - 1).terminatedTime = terminatedTime;
                terminatedList.add(pendingTerminatedTask); 
                numActiveTasks--;
            }

            // Free and abort blocked tasks in order of 1, 2, 3 ... n until resources are available again
            int number = 1;

            while (deadlockPossible) {
                Task currentTask = null;
                
                for (Task blockedTask : blockedQueue) {
                    if (blockedTask.taskNumber == number) {
                        currentTask = blockedTask;
                        break;
                    }
                }
                number++;

                if (currentTask == null || !currentTask.isBlocked) {
                    continue;
                }
                
                blockedQueue.remove(currentTask);

                int taskIndex = currentTask.taskNumber - 1;
                Task masterTask = masterTaskList.get(taskIndex);

                if (masterTask.numberGranted != 0)
                    blockedTasks--;
                
                int releaseIndex = currentTask.resourceType - 1;
                Task.maxClaims.set(releaseIndex, Task.maxClaims.get(releaseIndex) + masterTask.numberGranted);
                masterTask.numberGranted = 0;
                abortedTasks.add(currentTask.taskNumber);
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
                    deadlockPossible = false;
                }

            }

            cycle++;
        } // end outer while

        printInfo(masterTaskList, abortedTasks, "FIFO");
    }

    public static void printInfo(List<Task> masterTaskList, Set<Integer> abortedTasks, String algorithm) {
        System.out.print("\n\n");
        if (algorithm.equals("FIFO"))
            System.out.println("FIFO");
        else
            System.out.println("BANKERS");

        System.out.print("------------\n");
        int numTasks = masterTaskList.size();
        int totalTerminatedTime = 0;
        int totalWaitingTime = 0;

        for (int i = 1; i <= numTasks; i++) {
            System.out.print("Task " + i + "\t");
            if (abortedTasks.contains(i)){
                System.out.print("Aborted \n");
            }
            else {
                Task currentTask = masterTaskList.get(i - 1);
                int terminatedTime = currentTask.terminatedTime;
                int waitingTime = currentTask.waitingTime;

                totalTerminatedTime += terminatedTime;
                totalWaitingTime += waitingTime;

                System.out.print(terminatedTime + "\t" + waitingTime + "\t" + 
                (int)Math.rint((waitingTime/(double)(terminatedTime)) * 100) + "%\n");
            }   
        }

        System.out.print("Total\t" + totalTerminatedTime + "\t" + totalWaitingTime + "\t" + 
        (int)Math.rint((totalWaitingTime/(double)(totalTerminatedTime) * 100)) + "%\n");
    }
}
