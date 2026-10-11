package tutorengine.datastructures;

// Graded custom Stack (PROJECT_SPECS.md S7, LIFO): undo buffer / DFS.
// Linked nodes: push/pop/peek O(1), no shifting, no java.util.
public class CustomStack<T> {
    private static class Node<T> {
        T val; Node<T> next;
        Node(T v) { val = v; }
    }

    private Node<T> top;
    private int size;

    public void push(T v) { Node<T> n = new Node<>(v); n.next = top; top = n; size++; }
    public T pop() { if (top == null) return null; T v = top.val; top = top.next; size--; return v; }
    public T peek() { return top == null ? null : top.val; }
    public boolean isEmpty() { return top == null; }
    public int size() { return size; }

    public static void main(String[] args) {
        CustomStack<String> s = new CustomStack<>();
        s.push("MTG-OTJ-055"); s.push("MTG-MH3-001"); s.push("MTG-FDN-101");
        System.out.println("peek=" + s.peek() + " size=" + s.size());
        System.out.println("pop=" + s.pop() + " pop=" + s.pop() + " peek=" + s.peek());
        System.out.println("empty pop=" + new CustomStack<String>().pop() + " (null-safe)");
        System.out.println("CustomStack OK.");
    }
}
