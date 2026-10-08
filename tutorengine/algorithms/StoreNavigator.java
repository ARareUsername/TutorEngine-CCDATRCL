package tutorengine.algorithms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tutorengine.model.StorageLocation;

// OWNER: Huypungco — Graph & Benchmark. Do not edit files owned by others; use PR comments instead.
//
// PURPOSE: The physical store as a map: front nodes (checkout, intake desk,
// showcase) plus one 1x5 cabinet row (CAB-1..CAB-5), each cabinet with UPPER/LOWER
// shelf slots holding ~1000-card boxes. BFS answers "shortest walk from checkout
// to this card's box"; DFS audits every slot. Slot ids come from StorageLocation
// so the map and the alias table can never disagree.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.StoreNavigator
//   Expected: nodes=18 edges=21 (need >=10/15); shortest path Checkout ->
//   CAB-5-UPPER prints 3 hops; dfs() names all 18 nodes exactly once; a made-up
//   location yields an empty path.
//
// DEFENSE: both searches visit each node and edge once — O(V+E); the queue version
// guarantees shortest hops, the stack version does not.
// SPEC: S7 graph, S8 BFS/DFS, S11 both traversals, Template H traces, tests T07/T08.
public class StoreNavigator {
    public static final String CHECKOUT = "Checkout Counter";

    // 18 nodes / 21 edges: the canonical cabinet map (spec minimum S6: 10/15).
    public static Map<String, List<String>> sampleMap() {
        Map<String, List<String>> g = new HashMap<>();
        addNode(g, CHECKOUT);
        addNode(g, "Intake Sorting Desk");
        addNode(g, "Showcase Display");
        for (StorageLocation s : StorageLocation.slots()) addNode(g, s.getId());
        for (int n = 1; n <= 5; n++) addNode(g, "CAB-" + n);
        addEdge(g, CHECKOUT, "Intake Sorting Desk");
        addEdge(g, CHECKOUT, "Showcase Display");
        for (int n = 1; n <= 5; n++) addEdge(g, "Intake Sorting Desk", "CAB-" + n);
        for (int n = 1; n < 5; n++) addEdge(g, "CAB-" + n, "CAB-" + (n + 1));
        for (StorageLocation s : StorageLocation.slots()) addEdge(g, s.getCabinet(), s.getId());
        return g;
    }

    private static void addNode(Map<String, List<String>> g, String n) {
        g.putIfAbsent(n, new ArrayList<>());
    }

    private static void addEdge(Map<String, List<String>> g, String a, String b) {
        addNode(g, a); addNode(g, b);
        if (!g.get(a).contains(b)) g.get(a).add(b);
        if (!g.get(b).contains(a)) g.get(b).add(a);
    }

    public static int nodeCount(Map<String, List<String>> g) { return g.size(); }

    public static int edgeCount(Map<String, List<String>> g) {
        int n = 0;
        for (List<String> nb : g.values()) n += nb.size();
        return n / 2; // undirected
    }

    // BFS min-hop path; empty list = unknown/disconnected (never null/throw).
    public static List<String> bfsPath(Map<String, List<String>> g, String start, String goal) {
        List<String> none = new ArrayList<>();
        if (!g.containsKey(start) || !g.containsKey(goal)) return none;
        Map<String, String> parent = new HashMap<>();
        Set<String> seen = new HashSet<>();
        Deque<String> q = new ArrayDeque<>();
        q.offer(start); seen.add(start);
        while (!q.isEmpty()) {
            String cur = q.poll();
            if (cur.equals(goal)) break;
            for (String nxt : g.getOrDefault(cur, List.of()))
                if (seen.add(nxt)) { parent.put(nxt, cur); q.offer(nxt); }
        }
        if (!seen.contains(goal)) return none;
        List<String> path = new ArrayList<>();
        for (String c = goal; c != null; c = parent.get(c)) {
            path.add(0, c);
            if (c.equals(start)) break;
        }
        return path;
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
        Map<String, List<String>> g = sampleMap();
        System.out.println("nodes=" + nodeCount(g) + " edges=" + edgeCount(g) + " (need >=10/15)");
        List<String> path = bfsPath(g, CHECKOUT, "CAB-5-UPPER");
        System.out.println("BFS path: " + String.join(" -> ", path) + " (" + (path.size() - 1) + " hops)");
        List<String> audit = dfs(g, CHECKOUT);
        System.out.println("DFS audit (" + audit.size() + " nodes): " + String.join(" | ", audit));
        System.out.println("missing node: " + bfsPath(g, CHECKOUT, "Narnia") + " (empty-safe)");
        System.out.println("StoreNavigator OK.");
    }
}
