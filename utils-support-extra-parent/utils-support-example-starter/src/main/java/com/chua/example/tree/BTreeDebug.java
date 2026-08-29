package com.chua.example.tree;

import com.chua.common.support.tree.*;
import java.lang.reflect.*;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) throws Exception {
        BTree<Integer, String> bt = new BTree<>(4);
        for (int i = 0; i < 20; i++) {
            bt.put(i, "v" + i);
            printBT(bt, i);
        }
        System.out.println("\nsize=" + bt.size());
        int miss = 0;
        for (int j = 0; j < 20; j++) {
            if (!bt.containsKey(j)) miss++;
        }
        System.out.println("missing=" + miss);
    }

    static void printBT(BTree<Integer, String> tree, int insertIdx) throws Exception {
        Field rootField = BTree.class.getDeclaredField("root");
        rootField.setAccessible(true);
        Object root = rootField.get(tree);
        System.out.println("=== Insert " + insertIdx + " ===");
        printNode(root, 0);
    }

    static void printNode(Object node, int depth) throws Exception {
        String indent = "  ".repeat(depth);
        Field leafField = node.getClass().getDeclaredField("leaf");
        Field keysField = node.getClass().getDeclaredField("keys");
        Field valuesField = node.getClass().getDeclaredField("values");
        Field childrenField = node.getClass().getDeclaredField("children");
        leafField.setAccessible(true); keysField.setAccessible(true);
        valuesField.setAccessible(true); childrenField.setAccessible(true);
        boolean isLeaf = (boolean) leafField.get(node);
        List<?> keys = (List<?>) keysField.get(node);
        List<?> values = (List<?>) valuesField.get(node);
        List<?> children = (List<?>) childrenField.get(node);
        String inv = isLeaf ? "" : (children.size() == keys.size() + 1 ? " OK" : " BAD!" + " k=" + keys.size() + " c=" + children.size());
        System.out.println(indent + (isLeaf ? "LEAF" : "INT") + " k=" + keys + " v=" + values + " ch=" + children.size() + inv);
        if (!isLeaf) {
            for (Object child : children) printNode(child, depth + 1);
        }
    }
}
