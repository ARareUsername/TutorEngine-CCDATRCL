package tutorengine.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Hi Huypungco! This file is yours. Nobody else should edit it, and please
// don't edit anyone else's — comments on pull requests for anything cross-file.
//
// What this does: it turns the physical store into a map the program can
// reason about. Every spot (checkout, intake desk, showcase, bulk boxes) is a
// dot, every walkable aisle is a line between dots. Two questions get answered:
// "what's the shortest walk from checkout to this card's box?" (BFS — it checks
// all one-step-away spots, then all two-step ones, so the first route found is
// the shortest) and "which zones can we even reach?" (DFS — it strides deep
// down each aisle before backtracking).
//
// Your steps:
//  1. Finish bfsPath() using the parent-map trick: search outward from the
//     start, remember which spot you came from for each new spot, stop at the
//     goal, then follow the trail backwards to build the route. Unknown spot
//     or no route: print a message and return an empty list, never crash.
//  2. dfs() already works (it uses a stack to go deep first) — keep it.
//  3. In sampleMap(), build the real map: 10 spots and 15 aisle links (the spec
//     minimum). Suggestions: Checkout, Intake Desk, Showcase, Bulk A/B/C,
//     Supplies, Back Room, Exit, Grading Table. Aisles run both ways.
//  4. In main(), print the walk from Checkout to Bulk Box C and the full DFS
//     visit order. Later, Karl's search will call this with the found card's
//     box and print "Walk: A -> B -> C".
//  5. For the traces, draw two pages: the BFS waiting line and visited set
//     level by level, plus the DFS walk.
//  6. Rehearse this: the instructor may delete one aisle and ask you to rerun
//     the search live. Practice it until it's boring.
//
// How to check your work:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.StoreNavigator
//   When you're done: the printed walk really is the shortest (count the
//   arrows — no shorter route exists on your map), DFS names all 10 spots
//   exactly once, and a made-up location gets a message, not a crash.
//
// For the defense, be ready to say both searches visit each spot and aisle
// once (that's the O(V+E) line in the report), and why the queue version finds
// shortest walks while the stack version doesn't promise that. (Spec: S7 graph,
// S8 BFS/DFS, S11 both traversals, Template H traces, tests T07/T08.)
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
