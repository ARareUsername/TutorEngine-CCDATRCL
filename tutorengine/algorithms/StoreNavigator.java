package tutorengine.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// OWNER: Huypungco — Graph & Benchmark. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: Turns the physical store into a map the program can reason about.
// Every spot (checkout, intake desk, showcase, bulk boxes) is a node; every
// walkable aisle is an edge. BFS answers "shortest walk from checkout to this
// card's box" (it checks all 1-step spots, then all 2-step spots, so the first
// route found is the shortest). DFS answers "which zones are reachable at all"
// (it strides deep down each aisle before backtracking).
//
// HOW TO IMPLEMENT:
//   1. bfsPath(): breadth-first search with a parent map. Queue the start; when
//      a new spot is first reached, record parent.put(next, current). Stop when
//      the goal is dequeued, then rebuild the route by walking parent links from
//      goal back to start and reversing. Unknown start/goal or disconnected goal:
//      print a message and return an empty list, never null, never throw.
//   2. Keep dfs() as written (explicit ArrayDeque stack = iterative deep-first).
//   3. sampleMap(): build 10 spots and 15 aisle links (the spec minimum).
//      Suggested spots: Checkout Counter, Intake Sorting Desk, Showcase Display,
//      Bulk Box A/B/C, Supplies, Back Room, Exit, Grading Table. Aisles run both
//      directions — write one addEdge helper that appends each side, and count
//      the calls until 15.
//   4. main(): print bfsPath from "Checkout Counter" to "Bulk Box C
//      (Green/Colorless)" joined as "A -> B -> C", then print the full dfs()
//      order from Checkout. Later, Karl's search result feeds
//      Card.getBoxLocationId() into bfsPath() here.
//   5. Trace packet (2 pages): BFS waiting line + visited set after each level;
//      DFS visit order with the stack contents at each pop.
//   6. Rehearse deleting one aisle and rerunning bfsPath() — the instructor may
//      request exactly that live.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.StoreNavigator
//   Expected: the printed walk is the shortest possible (count the arrows — no
//   shorter route exists on the map); dfs() names all 10 spots exactly once; a
//   made-up location prints a message and yields an empty path.
//
// DEFENSE: both searches visit each node and edge once — that is the O(V+E)
// line in the report; the queue version guarantees shortest hops, the stack
// version does not.
// SPEC: S7 graph, S8 BFS/DFS, S11 both traversals, Template H traces, tests T07/T08.
public class StoreNavigator {
    public static Map<String, List<String>> sampleMap() {
        // TODO Huypungco: build and return the 10-node/15-edge map. Keep helper
        // separate so bfsPath/dfs stay clean. Undirected: add both directions.
        Map<String, List<String>> g = new HashMap<>();
        return g;
    }

    public static List<String> bfsPath(Map<String, List<String>> g, String start, String goal) {
        // TODO Huypungco: implement parent-map BFS (step 1). Stub below.
        System.out.println("TODO Huypungco: BFS min-hop " + start + " -> " + goal);
        return new ArrayList<>();
    }

    public static List<String> dfs(Map<String, List<String>> g, String start) {
        Set<String> seen = new HashSet<>();
        List<String> order = new ArrayList<>();
        Deque<String> st = new ArrayDeque<>();
        st.push(start);
        while (!st.isEmpty()) {
            String c = st.pop();
            if (!seen.add(c)) continue;
            order.add(c);
            for (String n : g.getOrDefault(c, List.of())) if (!seen.contains(n)) st.push(n);
        }
        return order;
    }

    public static void main(String[] args) {
        // TODO Huypungco: demo from step 3. Print path as "A -> B -> C".
        System.out.println("StoreNavigator. OWNER: Huypungco. (implement demo)");
    }
}
