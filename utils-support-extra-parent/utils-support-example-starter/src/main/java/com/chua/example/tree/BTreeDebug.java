package com.chua.example.tree;

import com.chua.common.support.tree.*;
import java.lang.reflect.*;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) throws Exception {
        // Test BTree with small order to trace easily
        BTree<Integer, String> bt = new BTree<>(4);
        for (int i = 0; i < 20; i++) {
            System.out.println("=== Insert " + i + " ===");
            bt.put(i, "v" + i);
            printBTree(bt);
            validateBT(bt);
        }
        System.out.println("\nsize=" + bt.size());
        int miss = 0;
        for (int j = 0; j < 20; j++) {
            if (!bt.containsKey(j)) miss++;
        }
        System.out.println("missing=" + miss);
    }

    static void printBTree(BTree<Integer, String> tree) throws Exception {
        Field rootField = BTree.class.getDeclaredField("root");
        rootField.setAccessible(true);
        Object root = rootField.get(tree);
        printNode(root, 0);
    }

    static void printNode(Object node, int depth) throws Exception {
        String indent = "  ".repeat(depth);
        Field leafField = node.getClass().getDeclaredField("leaf");
        Field keysField = node.getClass().getDeclaredField("keys");
        Field valuesField = node.getClass().getDeclaredField("values");
        Field childrenField = node.getClass().getDeclaredField("children");
        leafField.setAccessible(true);
        keysField.setAccessible(true);
        valuesField.setAccessible(true);
        childrenField.setAccessible(true);
        boolean isLeaf = (boolean) leafField.get(node);
        List<?> keys = (List<?>) keysField.get(node);
        List<?> values = (List<?>) valuesField.get(node);
        List<?> children = (List<?>) childrenField.get(node);
        String inv = isLeaf ? "" : (children.size() == keys.size() + 1 ? " OK" : " BAD!");
        System.out.println(indent + (isLeaf ? "LEAF" : "INT") + " keys=" + keys + " vals=" + values + " ch=" + children.size() + inv);
        if (!isLeaf) {
            for (Object child : children) printNode(child, depth + 1);
        }
    }

    static void validateBT(BTree<Integer, String> tree) throws Exception {
        Field rootField = BTree.class.getDeclaredField("root");
        rootField.setAccessible(true);
        Object root = rootField.get(tree);
        validateNode(root, Integer.MIN_VALUE, Integer.MAX_VALUE);
        System.out.println("  VALIDATE OK");
    }

    static int validateNode(Object node, int min, int max) throws Exception {
        Field leafField = node.getClass().getDeclaredField("leaf");
        Field keysField = node.getClass().getDeclaredField("keys");
        Field valuesField = node.getClass().getDeclaredField("values");
        Field childrenField = node.getClass().getDeclaredField("children");
        leafField.setAccessible(true);
        keysField.setAccessible(true);
        valuesField.setAccessible(true);
        childrenField.setAccessible(true);
        boolean isLeaf = (boolean) leafField.get(node);
        List<?> keys = (List<?>) keysField.get(node);
        List<?> children = (List<?>) childrenField.get(node);
        if (!isLeaf && children.size() != keys.size() + 1) {
            throw new RuntimeException("Invariant broken: keys=" + keys + " children=" + children.size());
        }
        if (!isLeaf) {
            for (int i = 0; i < keys.size(); i++) {
                int k = (Integer) keys.get(i);
                if (k <= min || k >= max) throw new RuntimeException("Key out of range: " + k);
                validateNode(children.get(i), min, k);
            }
            validateNode(children.get(keys.size()), keys.isEmpty() ? min : (Integer) keys.get(keys.size()-1), max);
        }
        return keys.isEmpty() ? min : (Integer) keys.get(keys.size()-1);
    }
}
