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

// OWNER: Huypungco — Graph & Benchmark. Admin edit by Bondoc (TutorEngine.md section 7):
// remodel sampleMap to mtg_storage_organization_guide.md slots (card-level routing).
//2026-10-11
// PURPOSE: The physical store as a map: front nodes (checkout, intake desk,
// showcase) plus the guide's box slots (black-rack tiers, white-cabinet shelves).
// BFS answers "shortest walk from checkout to this card's slot"; DFS audits every
// slot. Slot ids come from StorageLocation so the map and routing never disagree.
//
// HOW TO TEST:
//   Run: mvn -q exec:java -Dexec.mainClass=tutorengine.algorithms.StoreNavigator
//   Expected: nodes=12 edges=17 (need >=10/15); a TSP card routes to CAB-T-TOP
//   in 2 hops; dfs() names all 12 nodes exactly once; a made-up goal yields empty.
//
// DEFENSE: both searches visit each node and edge once — O(V+E); the queue version
// guarantees shortest hops, the stack version does not.
// SPEC: S7 graph, S8 BFS/DFS, S11 both traversals, Template H traces, tests T07/T08.
public class StoreNavigator {
    public static final String CHECKOUT = "Checkout Counter";

    // 12 nodes / 16 edges: desk-spoke aisles + rack-tier and cabinet-shelf chains.
    public static Map<String, List<String>> sampleMap() {
        Map<String, List<String>> g = new HashMap<>();
        addNode(g, CHECKOUT);
        addNode(g, "Intake Sorting Desk");
        addNode(g, "Showcase Display");
        for (StorageLocation s : StorageLocation.slots()) addNode(g, s.getId());
        addEdge(g, CHECKOUT, "Intake Sorting Desk");
        addEdge(g, CHECKOUT, "Showcase Display");
        for (StorageLocation s : StorageLocation.slots())
            addEdge(g, "Intake Sorting Desk", s.getId());
        addEdge(g, "RACK-M-TOP", "RACK-M-MID");
        addEdge(g, "RACK-M-MID", "RACK-PON-LOW");
        addEdge(g, "RACK-L-TOP", "RACK-PON-LOW");
        addEdge(g, "CAB-T-TOP", "CAB-UW-MID");
        addEdge(g, "CAB-LANDS-LOW", "CAB-TOKEN-LOW");
        addEdge(g, "CAB-TOKEN-LOW", "CAB-RARE-LOW");
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
        String tspSku = "";
        for (tutorengine.model.Card c : tutorengine.util.CSVLoader.load("Dataset/cards.csv"))
            if ("TSP".equals(c.getSetCode())) { tspSku = c.getSku(); break; }
        String slot = "";
        for (tutorengine.model.Card c : tutorengine.util.CSVLoader.load("Dataset/cards.csv"))
            if (c.getSku().equals(tspSku)) slot = StorageLocation.route(c);
        List<String> path = bfsPath(g, CHECKOUT, slot);
        System.out.println("TSP card " + tspSku + " routes to " + slot + ": "
                + String.join(" -> ", path) + " (" + (path.size() - 1) + " hops)");
        List<String> audit = dfs(g, CHECKOUT);
        System.out.println("DFS audit (" + audit.size() + " nodes): " + String.join(" | ", audit));
        System.out.println("missing node: " + bfsPath(g, CHECKOUT, "Narnia") + " (empty-safe)");
        System.out.println("StoreNavigator OK.");
    }
}
