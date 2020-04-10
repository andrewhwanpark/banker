import java.util.*;
import java.io.*;

public class lab3 {
    static int T; // # of tasks
    static int R; // # of resource types
    static ArrayList<Integer> units; // # of units present in each type
    static ArrayList<Activity> activities; // each line of activity is stored here
    static ArrayList<Activity> b_activities; // Same as above but for Banker's
    static ArrayList<Task> tasks; // Array of tasks
    static ArrayList<Task> b_tasks; // Same as above but for Banker's

    static String abort_message; // Message displayed when aborting tasks to resolve deadlocks

    public static void main(String[] args) throws Exception {
        String input = args[0]; // File input from terminal
        File file = new File(input);
        Scanner scanner = new Scanner(file);

        T = Integer.parseInt(scanner.next()); // # of tasks
        R = Integer.parseInt(scanner.next()); // # of resources

        // T & R input check, if it exceeds max value in java, abort program
        if (T > Integer.MAX_VALUE || R > Integer.MAX_VALUE) {
            System.out.println("T or R exceeds Integer.MAX_VALUE in Java");
            System.exit(1);
        }

        units = new ArrayList<>(); // Initialize units

        // Read units of each resource, add to units arraylist
        for (int i = 0; i < R; i++) {
            int unit = Integer.parseInt(scanner.next());
            units.add(unit);
        }

        activities = new ArrayList<>(); // Initialize activities
        b_activities = new ArrayList<>(); // Initialize activities for banker
        
        // Populate activities arraylist
        while (scanner.hasNext()) {
            String command = scanner.next();
            int task_number = Integer.parseInt(scanner.next());
            int delay = Integer.parseInt(scanner.next());
            int resource_type = Integer.parseInt(scanner.next());
            int number = Integer.parseInt(scanner.next());
            
            Activity a = new Activity(command, task_number, delay, resource_type, number);
            activities.add(a);

            Activity ba = new Activity(command, task_number, delay, resource_type, number);
            b_activities.add(ba);
        }

        scanner.close();

        tasks = new ArrayList<>(); // Initialize tasks
        b_tasks = new ArrayList<>(); // For Banker's

        // Populate task arraylist
        for (Activity a : activities) {
            if (a.command.equals("initiate")) {
                Task t = null;
                if (a.task_number - 1 >= tasks.size()) {
                    // Task obj doesn't exist
                    t = new Task(a.task_number);
                    tasks.add(t);
                } else {
                    // Task obj already exists
                    t = tasks.get(a.task_number - 1);
                }
                t.claims.put(a.resource_type, a.number);
            }

            tasks.get(a.task_number - 1).activities.add(a);
        }

        // Do the same for b_tasks
        for (Activity a : b_activities) {
            if (a.command.equals("initiate")) {
                Task t = null;
                if (a.task_number - 1 >= b_tasks.size()) {
                    // Task obj doesn't exist
                    t = new Task(a.task_number);
                    b_tasks.add(t);
                } else {
                    // Task obj already exists
                    t = b_tasks.get(a.task_number - 1);
                }
                t.claims.put(a.resource_type, a.number);
            }

            b_tasks.get(a.task_number - 1).activities.add(a);
        }

        // Populate task obj's resource list
        for (Task t : tasks) {
            for (int i = 0; i < units.size(); i++) {
                t.resource_list.add(0);
            }
        }

        for (Task t : b_tasks) {
            for (int i = 0; i < units.size(); i++) {
                t.resource_list.add(0);
            }
        }

        // Call optimistic
        ArrayList<Task> opterm = optimistic();
        // Call bankers
        ArrayList<Task> bterm = banker();
        // Print outputs
        print_output(opterm, bterm);
    }

    // Print output
    static void print_output(ArrayList<Task> opterm, ArrayList<Task> bterm) {
        String result = "";
        result += String.format("%14s%37s\n", "FIFO", "BANKER'S");

        int op_total_time = 0, op_wait_time = 0, b_total_time = 0, b_wait_time = 0;

        // Loop each terminated tasks
        for (int i = 0; i < T; i++) {
            Task t = opterm.get(i);
            Task bt = bterm.get(i);

            // Print optimistic manager outputs
            if (t.aborted == true) {
                result += String.format("%9s%2d%16s", "Task", t.task_number, "aborted");
            } else {
                op_total_time += t.total_time;
                op_wait_time += t.wait_time;

                float pct = (((float) t.wait_time) / ((float) t.total_time)) * 100;
                result += String.format("%9s%2d%7d%4d%4.0f%s", "Task", t.task_number, t.total_time, t.wait_time, pct, "%");
            }

            // Print banker's algo outputs
            if (bt.aborted == true) {
                result += String.format("%9s%2d%16s\n", "Task", bt.task_number, "aborted");
            } else {
                b_total_time += bt.total_time;
                b_wait_time += bt.wait_time;

                float pct = (((float) bt.wait_time) / ((float) bt.total_time)) * 100;
                result += String.format("%9s%2d%7d%4d%4.0f%s\n", "Task", bt.task_number, bt.total_time, bt.wait_time, pct, "%");
            }
        }

        // Print total outputs
        float total_pct = (((float) op_wait_time) / ((float) op_total_time)) * 100;
        float b_total_pct = (((float) b_wait_time) / ((float) b_total_time)) * 100;
        result += String.format("%10s%8d%4d%4.0f%s", "total", op_total_time, op_wait_time, total_pct, "%");
        result += String.format("%10s%8d%4d%4.0f%s\n", "total", b_total_time, b_wait_time, b_total_pct, "%");

        System.out.print(result);
    }

    // Banker's algo
    // Works with the help of safe() function that determines safe state through simulation
    // First, ensure initial claims don't exceed total units
    // Then, loop tasks until all tasks terminate
    // Deal with blocked tasks, by calling the safe() function with the blocked task in the running arraylist
    // Loop through running tasks, parse command and perform appropriate action
    // Move ready tasks & resources from last cycle into current cycle
    static ArrayList<Task> banker() {
        int cycle = 0; // Current Cycle

        ArrayList<Task> blocked = new ArrayList<>(); // Blocked tasks
        ArrayList<Task> running = new ArrayList<>(); // Running tasks
        ArrayList<Task> ready = new ArrayList<>(); // Tasks ready to run next cycle
        ArrayList<Task> terminated = new ArrayList<>(); // Finished tasks

        ArrayList<Integer> available = new ArrayList<>(); // Available resources
        ArrayList<Integer> available_next = new ArrayList<>(); // Free'd resources available in next cycle

        // Put all tasks in running
        for (Task t : b_tasks) {
            running.add(t);
        }

        // Add all resources to available
        for (int r : units) {
            available.add(r);
            available_next.add(0);
        }

        // Ensure initial claims do not exceed total units
        for (Iterator<Task> it = running.iterator(); it.hasNext();) {
            Task t = it.next();
            HashMap<Integer, Integer> map = t.claims; // claims hash table

            // Loop through entries of map
            for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
                if (entry.getValue() > units.get(entry.getKey() - 1)) {
                    // Abort task & delete from running list
                    System.out.println("Banker aborts Task " + t.task_number + " before run begins:");
                    System.out.println("Claim for Resource " + entry.getKey() + " (" + entry.getValue() + ") exceeds number of units present (" + units.get(entry.getKey() - 1) + ")");
                    t.aborted = true;
                    terminated.add(t);
                    it.remove(); // Remove from running
                }
            }
        }

        // Loop until all tasks terminated
        while (terminated.size() != T) {
            // Deal with blocked tasks
            for (Iterator<Task> it = blocked.iterator(); it.hasNext();) {
                Task t = it.next();
                Activity a = t.activities.get(0);

                t.wait_time++; // Increment wait time

                // New arraylist to pass to safe()
                // Adds the blocked task to an arraylist with all the running tasks
                ArrayList<Task> safelist = new ArrayList<>();

                // Add all running tasks to temporary safelist
                for (Task r : running) {
                    safelist.add(r);
                }

                // Add the blocked task to temporary safelist
                safelist.add(t);

                // Is the state safe with the blocked task?
                if (safe(safelist, available, a) == true) {
                    // Grant & update resources
                    t.resource_list.set(a.resource_type - 1, t.resource_list.get(a.resource_type - 1) + a.number);
                    available.set(a.resource_type - 1, available.get(a.resource_type - 1) - a.number);
                    // Unblock & add to ready
                    t.activities.remove(0);
                    ready.add(t);
                    it.remove();
                }
            }

            // Loop through running tasks
            for (Iterator<Task> it = running.iterator(); it.hasNext();) {
                Task t = it.next();
                Activity a = t.activities.get(0); // First activity of task
                
                // Parse commands and determine action
                switch (a.command) {
                    case "initiate":
                        // Remove activity from arraylist
                        t.activities.remove(a);
                        break;
                    case "request":
                        // Handle delays
                        if (a.delay != 0) {
                            a.delay--;
                        } else {
                            if (safe(running, available, a) == true) {
                                // Check request doesn't exceed task claim
                                if (a.number + t.resource_list.get(a.resource_type - 1) > t.claims.get(a.resource_type)) {
                                    // Abort task
                                    System.out.println("During cycle " + cycle + "-" + (cycle + 1) + " of Banker's Algorithm");
                                    System.out.println("Task " + t.task_number + "'s request exceeds its claim; aborted; released resources");

                                    // Release resources
                                    for (int i = 0; i < t.resource_list.size(); i++) {
                                        available.set(i, available.get(i) + t.resource_list.get(i));
                                    }

                                    t.aborted = true;
                                    terminated.add(t); 
                                    it.remove();
                                } else {
                                    // Grant
                                    t.resource_list.set(a.resource_type - 1, t.resource_list.get(a.resource_type - 1) + a.number);
                                    available.set(a.resource_type - 1, available.get(a.resource_type - 1) - a.number);
                                    t.activities.remove(a);
                                }
                            } else {
                                // Block task
                                blocked.add(t);
                                it.remove();
                            }
                        }
                        break;
                    case "release":
                        // Handle delays
                        if (a.delay != 0) {
                            a.delay--;
                        } else {
                            // Release resources
                            int request = a.number;

                            available_next.set(a.resource_type - 1, available_next.get(a.resource_type - 1) + request);
                            t.resource_list.set(a.resource_type - 1, t.resource_list.get(a.resource_type - 1) - request);
                            t.activities.remove(a);

                            // Handle terminate, since it does not require cycle
                            // Only terminate right away if there is no delay
                            if (t.activities.get(0).command.equals("terminate") && t.activities.get(0).delay == 0) {
                                t.total_time = cycle + 1;

                                // Free resources held by terminating task
                                for (int i = 0; i < t.resource_list.size(); i++) {
                                    available_next.set(i, t.resource_list.get(i) + available_next.get(i));
                                    t.resource_list.set(i, 0);
                                }

                                t.activities.remove(0);
                                terminated.add(t);
                                it.remove(); // Remove task from running
                            }
                        }
                        break;
                    case "terminate":
                        // Handle delay
                        if (a.delay != 0) {
                            a.delay--;
                        } else {
                            t.total_time = cycle;

                            // Free resources held by terminating task
                            for (int i = 0; i < t.resource_list.size(); i++) {
                                available_next.set(i, t.resource_list.get(i) + available_next.get(i));
                                t.resource_list.set(i, 0);
                            }

                            t.activities.remove(a);
                            terminated.add(t);
                            it.remove();
                        }
                        break;
                }
            }
             
            // Move ready tasks from last cycle into current cycle
            if (ready.size() != 0) {
                for (Iterator<Task> it = ready.iterator(); it.hasNext();) {
                    Task t = it.next();
                    running.add(t);
                    it.remove();
                }
            }

            // Move ready resources from last cycle into current cycle
            if (available_next.size() != 0) {
                for (int i = 0; i < available_next.size(); i++) {
                    available.set(i, available.get(i) + available_next.get(i));
                    available_next.set(i, 0);
                }
            }

            // Increment cycle
            cycle++;
        }

        // Sort objects based on task ID (for output)
        Collections.sort(terminated, (t1, t2) -> t1.task_number - t2.task_number);
        return terminated;
    }

    // Checks whether state is safe
    // Checks if any task can terminate satisfying (claim - allocated) units for all resource types
    // Simulate this process until all processes can terminate
    // If yes, return true, if not, return false
    // Move ready tasks & resources from last cycle into current cycle
    // Check if there is deadlock by calling deadlock() function
    static boolean safe(ArrayList<Task> running, ArrayList<Integer> available, Activity request) {
        // Clone arraylists
        ArrayList<Task> rc = new ArrayList<>(running.size());
        ArrayList<Integer> ac = new ArrayList<>(available.size());

        // Deep clone running
        for (Task t : running) {
            rc.add(new Task(t));
        }

        // Deep clone available
        for (int i : available) {
            ac.add(i);
        }

        // Grant request, see if state is safe
        ac.set(request.resource_type - 1, ac.get(request.resource_type - 1) - request.number);

        // If not enough units left to grant, unsafe
        if (ac.get(request.resource_type - 1) < 0)
            return false;

        // Find task that matches the task number of the request
        Task match = null;
        for (Task t : rc) {
            if (t.task_number == request.task_number)
                match = t;
        }

        // Update resource list of task
        match.resource_list.set(request.resource_type - 1, match.resource_list.get(request.resource_type - 1) + request.number);

        int size = rc.size(); // How many tasks?
        int completed = 0; // How many completed?

        while (true) {
            // If all tasks completed, return safe
            if (completed == size)
                return true;

            // Find a task that we can grant all remaining claims
            boolean found = false;
            int index = -1;

            // Loop through each task
            for (int i = 0; i < rc.size(); i++) {
                // Loop through each resource
                for (int j = 0; j < ac.size(); j++) {
                    // If this resource type can be satisfied its (claim - allocated) units
                    if (ac.get(j) >= rc.get(i).claims.get(j + 1) - rc.get(i).resource_list.get(j)) {
                        found = true;
                        index = i;
                    } else {
                        found = false;
                        index = -1;
                        break;
                    }
                }

                // Exit loop if task found
                if (found == true)
                    break;
            }

            // If the task is found
            if (index != -1) {
                // Pretend task terminated
                for (int i = 0; i < ac.size(); i++) {
                    ac.set(i, ac.get(i) + rc.get(index).resource_list.get(i));
                }

                completed++; // Increment completed tasks
                rc.remove(index); // Remove from running
            } else {
                return false;
            }
        }
    }

    // Optimistic manager
    // Works with deadlock() function to determine deadlock at end of each cycle
    // Loop through all tasks until all terminate
    // Deal with blocked tasks in FIFO manner, grant if possible, if not wait
    // Loop through running tasks
    // Parse command (initiate, request, etc) and determinate appropriate action
    static ArrayList<Task> optimistic() {
        int cycle = 0; // Current cycle

        ArrayList<Task> blocked = new ArrayList<>(); // Blocked tasks
        ArrayList<Task> running = new ArrayList<>(); // Running tasks
        ArrayList<Task> ready = new ArrayList<>(); // Tasks ready to run next cycle
        ArrayList<Task> terminated = new ArrayList<>(); // Finished tasks
        
        ArrayList<Integer> available = new ArrayList<>(); // Available resources
        ArrayList<Integer> available_next = new ArrayList<>(); // Free'd resources available in next cycle

        // Put all tasks in running
        for (Task t : tasks) {
            running.add(t);
        }

        // Add all resources to available
        for (int r : units) {
            available.add(r);
            available_next.add(0);
        }

        // Loop until all tasks terminate
        while (terminated.size() != T) {
            // Deal with blocked tasks in FIFO manner
            for (Iterator<Task> it = blocked.iterator(); it.hasNext();) {
                Task t = it.next(); // blocked task
                Activity a = t.activities.get(0); // First activity of blocked task

                int request = a.number; // resource request

                if (request > available.get(a.resource_type - 1)) {
                    // Can't grant
                    t.wait_time++;
                } else {
                    // Grant
                    t.wait_time++;
                    t.resource_list.set(a.resource_type - 1, t.resource_list.get(a.resource_type - 1) + request); // Update task's resource list
                    available.set(a.resource_type - 1, available.get(a.resource_type - 1) - request); // Update available resources
                    t.activities.remove(0); // Remove activity
                    ready.add(t); // Add to ready list
                    it.remove(); // Remove task from blocked
                }
            }

            // Loop through running tasks
            for (Iterator<Task> it = running.iterator(); it.hasNext();) {
                Task t = it.next();
                Activity a = t.activities.get(0); // First activity of task

                // Parse command and determine action
                switch(a.command) {
                    case "initiate":
                        // Remove activity from arraylist
                        t.activities.remove(a);
                        break;
                    case "request":
                        // Handle delays
                        if (a.delay != 0) {
                            // Delay (compute)
                            a.delay--;
                        } else {
                            // No delay
                            int request = a.number; // request of resource

                            if (request > available.get(a.resource_type - 1)) {
                                // Block task
                                blocked.add(t);
                                it.remove(); // Remove task from running list
                            } else {
                                // Allocate resources
                                t.resource_list.set(a.resource_type - 1, t.resource_list.get(a.resource_type - 1) + request); // Update task's resource list
                                available.set(a.resource_type - 1, available.get(a.resource_type - 1) - request); // Update available resources
                                t.activities.remove(a); // Remove activity from task
                            }
                        }
                        break;
                    case "release":
                        // Handle delays
                        if (a.delay != 0) {
                            a.delay--;
                        } else {
                            // Release resources
                            int request = a.number;

                            available_next.set(a.resource_type - 1, available_next.get(a.resource_type - 1) + request);
                            t.resource_list.set(a.resource_type - 1, t.resource_list.get(a.resource_type - 1) - request);
                            t.activities.remove(a);

                            // Handle terminate, since it does not require cycle
                            // Only terminate right away if there is no delay
                            if (t.activities.get(0).command.equals("terminate") && t.activities.get(0).delay == 0) {
                                t.total_time = cycle + 1;

                                // Free resources held by terminating task
                                for (int i = 0; i < t.resource_list.size(); i++) {
                                    available_next.set(i, t.resource_list.get(i) + available_next.get(i));
                                    t.resource_list.set(i, 0);
                                }

                                t.activities.remove(0);
                                terminated.add(t);
                                it.remove(); // Remove task from running
                            }
                        }
                        break;
                    case "terminate":
                        // Handle delay
                        if (a.delay != 0) {
                            a.delay--;
                        } else {
                            t.total_time = cycle;

                            // Free resources held by terminating task
                            for (int i = 0; i < t.resource_list.size(); i++) {
                                available_next.set(i, t.resource_list.get(i) + available_next.get(i));
                                t.resource_list.set(i, 0);
                            }

                            t.activities.remove(a);
                            terminated.add(t);
                            it.remove();
                        }
                        break;
                }
                
            }

            // Handle ready tasks (tasks for next cycle)
            if (ready.size() != 0) {
                for (Iterator<Task> it = ready.iterator(); it.hasNext();) {
                    Task t = it.next();
                    running.add(t);
                    it.remove();
                }
            }

            // Handle resources available in the next cycle
            if (available_next.size() != 0) {
                for (int i = 0; i < available_next.size(); i++) {
                    available.set(i, available.get(i) + available_next.get(i));
                    available_next.set(i, 0);
                }
            }

            // Handle deadlocks
            while (deadlock(blocked, running, available) == true) {
                int lp_index = 0; // lowest priority task

                // Find task with lowest number
                for (int i = 1; i < blocked.size(); i++) {
                    if (blocked.get(i).task_number < blocked.get(lp_index).task_number)
                        lp_index = i;
                }

                // Free resources
                for (int i = 0; i < blocked.get(lp_index).resource_list.size(); i++) {
                    available.set(i, available.get(i) + blocked.get(lp_index).resource_list.get(i));
                    blocked.get(lp_index).resource_list.set(i, 0);
                }

                // Abort task
                abort_message += "Optimistic Resource Manager aborted Task " + blocked.get(lp_index).task_number + "to resolve deadlock";
                blocked.get(lp_index).aborted = true;
                blocked.get(lp_index).wait_time = 0;
                blocked.get(lp_index).total_time = 0;
                terminated.add(blocked.get(lp_index));
                blocked.remove(lp_index);
            }

            // Increment cycle
            cycle++;
        }

        // Sort objects based on task ID
        Collections.sort(terminated, (t1, t2) -> t1.task_number - t2.task_number);
        return terminated;
    }

    // Check for deadlock
    static boolean deadlock(ArrayList<Task> blocked, ArrayList<Task> running, ArrayList<Integer> available) {
        // If no blocked tasks OR running tasks present, no deadlock
        if (blocked.size() == 0 || running.size() != 0) {
            return false;
        }

        boolean deadlock = true;

        // If any blocked task can be allocated resources, no deadlock
        for (Task t : blocked) {
            Activity a = t.activities.get(0);
            int request = a.number;

            if (available.get(a.resource_type - 1) >= request) {
                deadlock = false;
            }
        }

        return deadlock;
    }
}

// Each task is associated with Task obj
// activities arraylist stores associated activity objs
// resource_list stores resources & units held by task
class Task {
    int task_number; // Task #

    ArrayList<Activity> activities; // Activities for task
    ArrayList<Integer> resource_list; // List of resources held
    // ArrayList<Integer> claims; // Claims of each resource
    HashMap<Integer, Integer> claims;
    boolean aborted;
    
    // These vars are used for output
    int total_time;
    int wait_time;

    // Constructor
    Task(int task_number) {
        this.task_number = task_number;
        this.activities = new ArrayList<>();
        this.resource_list = new ArrayList<>();
        this.claims = new HashMap<>();
        this.aborted = false;
        this.total_time = 0;
        this.wait_time = 0;
    }

    // Constructor used for deep copy (safe())
    Task(Task t) {
        this.task_number = t.task_number;
        this.activities = new ArrayList<>();

        for (Activity a : t.activities) {
            this.activities.add(new Activity(a));
        }

        this.resource_list = new ArrayList<>();

        for (int i : t.resource_list) {
            this.resource_list.add(i);
        }

        this.claims = t.claims;
        this.aborted = t.aborted;
        this.total_time = t.total_time;
        this.wait_time = t.wait_time;
    }
}

// Class that stores each line of input (ex: release 0 1 1 1)
// These activitiy objs will be stored in Task's activities arraylist
class Activity {
    String command; // initiate, request, release, terminate
    int task_number; // Task #
    int delay; // Delay, unused for initiate
    int resource_type; // unused for terminate
    int number; // Initial claim for initiate, # requested for request & release, unused for terminate

    // Constructor
    Activity(String command, int task_number, int delay, int resource_type, int number) {
        this.command = command;
        this.task_number = task_number;
        this.delay = delay;
        this.resource_type = resource_type;
        this.number = number;
    }

    // Constructor for deep copy
    Activity(Activity a) {
        this.command = a.command;
        this.task_number = a.task_number;
        this.delay = a.delay;
        this.resource_type = a.resource_type;
        this.number = a.number;
    }
}
