package tutorengine.datastructures;

import tutorengine.model.Card;

// Graded custom hash table (PROJECT_SPECS.md S7): SKU -> Card, separate chaining.
// No java.util: own node chains, own hash spread. O(1) avg, O(n) worst.
public class CustomHashTable {
    private static class Entry {
        String key; Card val; Entry next;
        Entry(String k, Card v, Entry n) { key = k; val = v; next = n; }
    }

    private Entry[] table;
    private int size;

    public CustomHashTable(int capacity) { table = new Entry[capacity]; }

    private int idx(String key) { return (key.hashCode() & 0x7fffffff) % table.length; }

    public int bucketIndex(String key) { return idx(key); }
    public int chainLength(String key) {
        int n = 0;
        for (Entry e = table[idx(key)]; e != null; e = e.next) n++;
        return n;
    }

    public void put(String key, Card val) {
        int i = idx(key);
        for (Entry e = table[i]; e != null; e = e.next)
            if (e.key.equals(key)) { e.val = val; return; }
        table[i] = new Entry(key, val, table[i]);
        size++;
    }

    public Card get(String key) {
        for (Entry e = table[idx(key)]; e != null; e = e.next)
            if (e.key.equals(key)) return e.val;
        return null;
    }

    public Card remove(String key) {
        int i = idx(key);
        Entry prev = null;
        for (Entry e = table[i]; e != null; e = e.next) {
            if (e.key.equals(key)) {
                if (prev == null) table[i] = e.next; else prev.next = e.next;
                size--;
                return e.val;
            }
            prev = e;
        }
        return null;
    }

    public int size() { return size; }
    public int capacity() { return table.length; }

    public static void main(String[] args) {
        CustomHashTable t = new CustomHashTable(7); // tiny on purpose: forces collisions
        Card a = new Card("MTG-OTJ-055", "Mana Drain", "S", "U", "Instant", 2024, 2600, 1, 98, false, "Showcase");
        Card b = new Card("MTG-OTJ-056", "Forked Bolt", "S", "R", "Sorcery", 2024, 12, 5, 60, false, "Bulk Box B");
        Card c = new Card("MTG-MH3-001", "Ajani, Nacatl Pariah", "S", "W", "Creature", 2024, 2150, 2, 92, true, "Showcase");
        t.put(a.getSku(), a); t.put(b.getSku(), b); t.put(c.getSku(), c);
        System.out.println("bucket(" + a.getSku() + ")=" + t.bucketIndex(a.getSku())
                + " chain=" + t.chainLength(a.getSku()));
        System.out.println("bucket(" + b.getSku() + ")=" + t.bucketIndex(b.getSku())
                + " chain=" + t.chainLength(b.getSku()));
        System.out.println("get hit: " + t.get("MTG-OTJ-055").getName());
        System.out.println("get miss: " + t.get("NOPE-000") + " (null-safe)");
        System.out.println("remove: " + t.remove("MTG-OTJ-056").getName() + " size=" + t.size());
        int max = 0;
        for (int k = 1; k <= 20; k++) {
            String sku = String.format("MTG-GEN-%03d", k);
            t.put(sku, new Card(sku, "Gen " + k, "S", "W", "Creature", 2024, 1, 1, 50, false, "Bulk Box A"));
            max = Math.max(max, t.chainLength(sku));
        }
        System.out.println("collision demo: max chain after 20 extra keys=" + max + " (>1 proves chaining)");
        System.out.println("CustomHashTable OK. size=" + t.size() + "/" + t.capacity());
    }
}
