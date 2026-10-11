package tutorengine.datastructures;

// Graded custom Queue (PROJECT_SPECS.md S7, FIFO): intake staging / BFS.
// Linked head/tail: offer/poll O(1), no java.util.
public class CustomQueue<T> {
    private static class Node<T> {
        T val; Node<T> next;
        Node(T v) { val = v; }
    }

    private Node<T> head, tail;
    private int size;

    public void offer(T v) {
        Node<T> n = new Node<>(v);
        if (tail == null) head = tail = n;
        else { tail.next = n; tail = n; }
        size++;
    }

    public T poll() {
        if (head == null) return null;
        T v = head.val; head = head.next;
        if (head == null) tail = null;
        size--;
        return v;
    }

    public T peek() { return head == null ? null : head.val; }
    public boolean isEmpty() { return head == null; }
    public int size() { return size; }

    public static void main(String[] args) {
        CustomQueue<String> q = new CustomQueue<>();
        q.offer("MTG-OTJ-055"); q.offer("MTG-MH3-001"); q.offer("MTG-FDN-101");
        System.out.println("peek=" + q.peek() + " size=" + q.size());
        System.out.println("poll=" + q.poll() + " poll=" + q.poll() + " peek=" + q.peek());
        System.out.println("empty poll=" + new CustomQueue<String>().poll() + " (null-safe)");
        System.out.println("CustomQueue OK.");
    }
}
