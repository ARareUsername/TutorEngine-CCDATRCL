package tutorengine.datastructures;

import java.util.ArrayList;
import java.util.List;

// Graded custom Linked List (PROJECT_SPECS.md S7). Backs history/logs/audit trails.
// Minor java.util use: ArrayList only in toList() snapshot for printing/sorting.
public class CustomLinkedList<T> {
    private static class Node<T> {
        T val; Node<T> next;
        Node(T v) { val = v; }
    }

    private Node<T> head, tail;
    private int size;

    public void addLast(T v) {
        Node<T> n = new Node<>(v);
        if (head == null) head = tail = n;
        else { tail.next = n; tail = n; }
        size++;
    }

    public void addFirst(T v) {
        Node<T> n = new Node<>(v);
        n.next = head; head = n;
        if (tail == null) tail = n;
        size++;
    }

    public T removeFirst() {
        if (head == null) return null;
        T v = head.val; head = head.next;
        if (head == null) tail = null;
        size--;
        return v;
    }

    public boolean remove(T v) {
        Node<T> prev = null, cur = head;
        while (cur != null) {
            if ((v == null && cur.val == null) || (v != null && v.equals(cur.val))) {
                if (prev == null) head = cur.next; else prev.next = cur.next;
                if (cur == tail) tail = prev;
                size--;
                return true;
            }
            prev = cur; cur = cur.next;
        }
        return false;
    }

    public boolean contains(T v) {
        for (Node<T> c = head; c != null; c = c.next)
            if ((v == null && c.val == null) || (v != null && v.equals(c.val))) return true;
        return false;
    }

    public int size() { return size; }
    public boolean isEmpty() { return size == 0; }

    public List<T> toList() {
        List<T> out = new ArrayList<>(size);
        for (Node<T> c = head; c != null; c = c.next) out.add(c.val);
        return out;
    }

    public void printAll() {
        int i = 1;
        for (Node<T> c = head; c != null; c = c.next) System.out.println(i++ + ". " + c.val);
        if (size == 0) System.out.println("(empty log)");
    }

    public static void main(String[] args) {
        CustomLinkedList<String> t = new CustomLinkedList<>();
        t.addLast("INTAKE MTG-OTJ-055 x1");
        t.addLast("INTAKE MTG-MH3-001 x2");
        t.addLast("DELETE MTG-FDN-101");
        t.printAll();
        System.out.println("undo -> " + t.removeFirst() + " (head pop demo; size=" + t.size() + ")");
        t.printAll();
        System.out.println("contains DELETE? " + t.contains("DELETE MTG-FDN-101"));
        System.out.println("CustomLinkedList OK. size=" + t.size());
    }
}
