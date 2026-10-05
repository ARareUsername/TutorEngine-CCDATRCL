package tutorengine.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// OWNER: Mate D (Graph & Benchmark). No other member edits this file.
// TASK: BFS shortest path + DFS audit over Map<String,List<String>> adjacency.
// ACCEPT: bfsPath(Checkout->target) returns min-hop route; dfs() returns visit order; missing node safe.
// DEFENSE: O(V+E) each; live-change: remove edge + rerun BFS.
// SPEC: S7 Graph, S8 BFS/DFS, S11 BFS+DFS, Template H BFS/DFS traces.
public class StoreNavigator {
    public static List<String> bfsPath(Map<String, List<String>> g, String start, String goal) {
        System.out.println("TODO Mate D: BFS min-hop " + start + " -> " + goal);
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
        System.out.println("StoreNavigator stub. OWNER: Mate D.");
    }
}
