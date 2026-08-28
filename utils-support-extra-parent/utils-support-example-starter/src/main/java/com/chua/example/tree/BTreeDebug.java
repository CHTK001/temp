package com.chua.example.tree;

import com.chua.common.support.tree.BPlusTree;
import com.chua.common.support.tree.BPlusTreeNode;
import java.util.*;

public class BTreeDebug {
    public static void main(String[] args) {
        BPlusTree<Integer, String> plus = new BPlusTree<>(4);
        for (int i = 0; i < 15; i++) {
            System.out.println("=== Insert " + i + " ===");
            try {
                plus.put(i, "v" + i);
                System.out.println("  OK, size=" + plus.size());
                printTree(plus);
            } catch (Exception e) {
                System.out.println("  ERROR at insert " + i + ": " + e);
                e.printStackTrace();
                break;
            }
        }
    }

    static void printTree(BPlusTree<Integer, String> tree) {
        try {
            java.lang.reflect.Field rootField = BPlusTree.class.getDeclaredField("root");
            rootField.setAccessible(true);
            BPlusTreeNode<Integer, String> root = (BPlusTreeNode<Integer, String>) rootField.get(tree);
            printNode(root, 0);
        } catch (Exception e) {
            System.out.println("  (cannot inspect tree internals)");
        }
    }

    static void printNode(BPlusTreeNode<Integer, String> node, int depth) {
        String indent = "  ".repeat(depth);
        String type = node.isLeaf() ? "LEAF" : "INT";
        System.out.println(indent + type + " keys=" + node.keys + " children=" + node.children.size() + " next=" + (node.next == null ? "null" : "..."));
        if (!node.isLeaf()) {
            for (BPlusTreeNode<Integer, String> child : node.children) {
                printNode(child, depth + 1);
            }
        }
    }
}
