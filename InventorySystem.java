// InventorySystem.java
// Version: 1.6.0
// Last Updated: 2025-11-24
// Revision notes:
//  - FAST mode messages color-coded for auto-selection and immediate commit
//  - Menu exit is "X" with rearranged numbering
//  - Inventory automatically sorted alphabetically by product name
//  - Preserved SAFE/FAST modes, undo, and autocomplete
//  - Developer annotations maintained

import java.util.ArrayList;
import java.util.Scanner;
import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Comparator;

public class InventorySystem {

    public static final String VERSION = "1.6.0";

    public static class Product {
        public String name;
        public int quantity;
        public double cost;
        public double sellingPrice;

        public Product(String name, int quantity, double cost, double sellingPrice) {
            this.name = name;
            this.quantity = quantity;
            this.cost = cost;
            this.sellingPrice = sellingPrice;
        }
    }

    public static class Sale {
        public String productName;
        public int quantitySold;
        public double unitPrice;

        public Sale(String productName, int quantitySold, double unitPrice) {
            this.productName = productName;
            this.quantitySold = quantitySold;
            this.unitPrice = unitPrice;
        }
    }

    public static class Action {
        public String type; // "ADD" or "SALE"
        public String productName;
        public int qty;
        public boolean createdNew;
        public double prevSellingPrice;
        public int prevQuantity;
        public double unitPrice;

        public Action(String type, String productName, int qty) {
            this.type = type;
            this.productName = productName;
            this.qty = qty;
        }
    }

    public static ArrayList<Product> inventory = new ArrayList<>();
    public static ArrayList<Sale> sales = new ArrayList<>();
    public static ArrayList<Action> actionHistory = new ArrayList<>();
    public static Scanner input = new Scanner(System.in);
    public static String folder = "D:\\Visual Studio\\Source\\Repos\\InventorySystem\\";
    public static boolean SAFE_MODE = true;

    // ------------------------- COLOR HELPERS -------------------------
    public static void printSuccess(String msg) { System.out.println("\u001B[32m" + msg + "\u001B[0m"); }
    public static void printError(String msg) { System.out.println("\u001B[31m" + msg + "\u001B[0m"); }
    public static void printInfo(String msg) { System.out.println("\u001B[33m" + msg + "\u001B[0m"); }
    public static void printPrompt(String msg) { System.out.print("\u001B[36m" + msg + "\u001B[0m"); }
    public static void printHeading(String msg) { System.out.println("\u001B[34;1m" + msg + "\u001B[0m"); }

    public static void printFastAuto(String msg) { System.out.println("\u001B[35m" + msg + "\u001B[0m"); } // magenta
    public static void printFastCommit(String msg) { System.out.println("\u001B[92m" + msg + "\u001B[0m"); } // bright green

    // ------------------------- MAIN -------------------------
    public static void main(String[] args) {
        printHeading("Inventory System - Version " + VERSION);
        chooseMode();
        loadInventoryFromFile();
        printSuccess("Inventory loaded successfully.");

        while (true) {
            printMainMenu();
            printPrompt("Choose an option (1-6 or X to exit): ");
            String choice = input.nextLine().trim().toUpperCase();

            switch (choice) {
                case "1": addInventoryFlow(); break;
                case "2": viewInventory(); break;
                case "3": recordSaleFlow(); break;
                case "4": printInventoryReport(); break;
                case "5": printSalesReport(); break;
                case "6": undoLastAction(); break;
                case "X":
                    saveInventoryToFile();
                    printSuccess("Inventory saved successfully. Exiting system...");
                    return;
                default:
                    printError("Invalid option. Try again.");
            }
        }
    }

    // ------------------------- MODE -------------------------
    public static void chooseMode() {
        while (true) {
            printHeading("Select Mode:");
            System.out.println("1. SAFE MODE (confirmation summary before commit)");
            System.out.println("2. FAST MODE (minimal prompts, faster entry)");
            printPrompt("Choose (1 or 2): ");
            String s = input.nextLine().trim();
            if (s.equals("1")) { SAFE_MODE = true; printSuccess("SAFE MODE selected."); return; }
            else if (s.equals("2")) { SAFE_MODE = false; printSuccess("FAST MODE selected."); return; }
            else { printError("Invalid. Enter 1 or 2."); }
        }
    }

    // ------------------------- MENU -------------------------
    public static void printMainMenu() {
        printHeading("\n=== SARI-SARI STORE INVENTORY SYSTEM ===");
        System.out.println("1. Add Purchased Inventory");
        System.out.println("2. View Current Inventory");
        System.out.println("3. Record Sales");
        System.out.println("4. Print Inventory Report");
        System.out.println("5. Print Sales Report");
        System.out.println("6. UNDO last action");
        System.out.println("X. Exit");
    }

    // ------------------------- FLOWS -------------------------
    public static void addInventoryFlow() {
        SelectionResult sel = selectOrCreateProduct("add");
        if (sel == null) return;

        Integer qty = readIntOrCancel("Enter quantity bought or type CANCEL to abort: ");
        if (qty == null) { printInfo("Operation cancelled."); return; }

        Double cost = readDoubleOrCancel("Enter cost per unit or type CANCEL to abort: ");
        if (cost == null) { printInfo("Operation cancelled."); return; }

        Double sellingPrice = readDoubleOrCancel("Enter selling price per unit or type CANCEL to abort (0 to leave unset): ");
        if (sellingPrice == null) { printInfo("Operation cancelled."); return; }

        if (SAFE_MODE) {
            printHeading("\n--- Summary (Add Inventory) ---");
            System.out.println("Product: " + sel.displayName);
            System.out.println("Quantity to add: " + qty);
            System.out.println("Cost per unit: " + cost);
            System.out.println("Selling price per unit: " + sellingPrice);
            String conf = readYesNoCancel("Save this purchase? (y/n or CANCEL): ");
            if (conf == null) { printInfo("Operation cancelled."); return; }
            if (conf.equals("n")) { printInfo("Operation aborted by user."); return; }
        }

        commitAdd(sel, qty, cost, sellingPrice);
        if (!SAFE_MODE) printFastCommit("Inventory updated immediately (FAST mode).");
        else printSuccess("Inventory updated.");
    }

    public static void recordSaleFlow() {
        SelectionResult sel = selectOrCreateProduct("sell");
        if (sel == null) return;

        Product selected = sel.product;
        if (selected == null) { printError("Cannot sell a product that doesn't exist."); return; }

        if (selected.sellingPrice <= 0) {
            Double sp = readDoubleOrCancel("No selling price set. Enter selling price per unit or CANCEL to abort: ");
            if (sp == null) { printInfo("Operation cancelled."); return; }
            selected.sellingPrice = sp;
        }

        Integer qty = readIntOrCancel("Enter quantity sold or type CANCEL to abort: ");
        if (qty == null) { printInfo("Operation cancelled."); return; }
        if (qty > selected.quantity) { printError("Not enough stock! Available: " + selected.quantity); return; }

        if (SAFE_MODE) {
            printHeading("\n--- Summary (Record Sale) ---");
            System.out.println("Product: " + selected.name);
            System.out.println("Quantity to sell: " + qty);
            System.out.println("Unit price: " + selected.sellingPrice);
            String conf = readYesNoCancel("Confirm sale? (y/n or CANCEL): ");
            if (conf == null) { printInfo("Operation cancelled."); return; }
            if (conf.equals("n")) { printInfo("Operation aborted by user."); return; }
        }

        commitSale(selected, qty, selected.sellingPrice);
        if (!SAFE_MODE) printFastCommit("Sale recorded immediately (FAST mode).");
        else printSuccess("Sale recorded.");
    }

    // ------------------------- SELECTION -------------------------
    public static class SelectionResult {
        public Product product;
        public String displayName;
        public boolean createdNew;
    }

    public static SelectionResult selectOrCreateProduct(String flowType) {
        while (true) {
            printPrompt("Enter product name (or start of name) or type CANCEL to abort: ");
            String inputName = input.nextLine();
            if (inputName.equalsIgnoreCase("CANCEL")) { printInfo("Operation cancelled."); return null; }

            ArrayList<Product> matches = findMatches(inputName);

            if (matches.isEmpty()) {
                if (flowType.equals("sell")) {
                    printInfo("No matching product found.");
                    String doCreate = readYesNoCancel("Do you want to create a new product with this name? (y/n or CANCEL): ");
                    if (doCreate == null || doCreate.equals("n")) { printInfo("Operation cancelled."); return null; }
                    SelectionResult res = new SelectionResult();
                    res.product = null; res.displayName = inputName; res.createdNew = true;
                    if (!confirmName(inputName)) return null;
                    return res;
                } else {
                    printInfo("No matching product found. Will create new product: " + inputName);
                    if (!confirmName(inputName)) continue;
                    SelectionResult res = new SelectionResult();
                    res.product = null; res.displayName = inputName; res.createdNew = true;
                    return res;
                }
            }

            if (matches.size() == 1) {
                Product p = matches.get(0);
                if (!SAFE_MODE) { 
                    printFastAuto("Auto-selected: " + p.name);
                    SelectionResult res = new SelectionResult(); res.product = p; res.displayName = p.name; res.createdNew = false; 
                    return res; 
                } else {
                    printInfo("Found: " + p.name);
                    if (!confirmName(p.name)) continue;
                    SelectionResult res = new SelectionResult();
                    res.product = p; res.displayName = p.name; res.createdNew = false;
                    return res;
                }
            }

            printInfo("Matching items:");
            for (int i = 0; i < matches.size(); i++) System.out.println((i + 1) + ". " + matches.get(i).name);
            printPrompt("Select number of item, 0 to add new, or type CANCEL to abort: ");
            String pick = input.nextLine();
            if (pick.equalsIgnoreCase("CANCEL")) { printInfo("Operation cancelled."); return null; }

            int idx;
            try { idx = Integer.parseInt(pick); } catch (Exception e) { printError("Invalid input. Restarting selection."); continue; }

            if (idx == 0) { if (!confirmName(inputName)) continue; SelectionResult res = new SelectionResult(); res.product = null; res.displayName = inputName; res.createdNew = true; return res; }
            else if (idx >= 1 && idx <= matches.size()) { if (!confirmName(matches.get(idx-1).name)) continue; SelectionResult res = new SelectionResult(); res.product = matches.get(idx-1); res.displayName = matches.get(idx-1).name; res.createdNew = false; return res; }
            else { printError("Invalid selection. Restarting selection."); continue; }
        }
    }

    public static ArrayList<Product> findMatches(String inputName) {
        ArrayList<Product> matches = new ArrayList<>();
        String lower = inputName.toLowerCase().trim();
        for (Product p : inventory) { if (p.name.toLowerCase().startsWith(lower)) matches.add(p); }
        return matches;
    }

    public static boolean confirmName(String name) {
        while (true) {
            printPrompt("Is the product name correct? (y/n or CANCEL): ");
            String confirm = input.nextLine().trim().toLowerCase();
            if (confirm.equals("y") || confirm.equals("yes")) return true;
            if (confirm.equals("n") || confirm.equals("no")) { printInfo("Please re-enter product name."); return false; }
            if (confirm.equals("cancel")) { printInfo("Operation cancelled."); return false; }
            printError("Invalid input. Please type y/n or CANCEL.");
        }
    }

    public static int readInt(String prompt) {
        while (true) { try { printPrompt(prompt); return Integer.parseInt(input.nextLine().trim()); } catch (Exception e) { printError("Invalid number. Try again."); } }
    }

    public static Integer readIntOrCancel(String prompt) {
        while (true) { try { printPrompt(prompt); String s = input.nextLine().trim(); if (s.equalsIgnoreCase("CANCEL")) return null; return Integer.parseInt(s); } catch (Exception e) { printError("Invalid number. Try again or type CANCEL."); } }
    }

    public static Double readDoubleOrCancel(String prompt) {
        while (true) { try { printPrompt(prompt); String s = input.nextLine().trim(); if (s.equalsIgnoreCase("CANCEL")) return null; return Double.parseDouble(s); } catch (Exception e) { printError("Invalid number. Try again or type CANCEL."); } }
    }

    public static String readYesNoCancel(String prompt) {
        while (true) { printPrompt(prompt); String s = input.nextLine().trim().toLowerCase(); if (s.equals("y") || s.equals("yes")) return "y"; if (s.equals("n") || s.equals("no")) return "n"; if (s.equalsIgnoreCase("cancel")) return null; printError("Invalid input. Please type y/n or CANCEL."); }
    }

    // ------------------------- COMMIT / UNDO -------------------------
    public static void commitAdd(SelectionResult sel, int qty, double cost, double sellingPrice) {
        if (sel.product != null) {
            Product p = sel.product;
            Action act = new Action("ADD", p.name, qty);
            act.createdNew = false; act.prevQuantity = p.quantity; act.prevSellingPrice = p.sellingPrice;
            p.quantity += qty; p.cost = cost; if (sellingPrice > 0) p.sellingPrice = sellingPrice;
            actionHistory.add(act);
        } else {
            Product p = new Product(sel.displayName, qty, cost, sellingPrice);
            inventory.add(p);
            Action act = new Action("ADD", p.name, qty);
            act.createdNew = true; actionHistory.add(act);
        }

        // Sort alphabetically after add
        inventory.sort(Comparator.comparing(prod -> prod.name.toLowerCase()));
    }

    public static void commitSale(Product product, int qty, double unitPrice) {
        int beforeQty = product.quantity;
        product.quantity -= qty;
        sales.add(new Sale(product.name, qty, unitPrice));
        Action act = new Action("SALE", product.name, qty); act.prevQuantity = beforeQty; act.unitPrice = unitPrice;
        actionHistory.add(act);
    }

    public static void undoLastAction() {
        if (actionHistory.isEmpty()) { printInfo("Nothing to undo."); return; }
        Action a = actionHistory.remove(actionHistory.size() - 1);
        if (a.type.equals("ADD")) {
            Product p = findProductByName(a.productName);
            if (p == null) { printError("Undo: product not found."); return; }
            if (a.createdNew) { inventory.remove(p); printSuccess("Undo: removed newly added product '" + a.productName + "'."); }
            else { p.quantity = a.prevQuantity; p.sellingPrice = a.prevSellingPrice; printSuccess("Undo: reverted ADD for '" + a.productName + "'. Quantity restored to " + p.quantity + "."); }
        } else if (a.type.equals("SALE")) {
            Product p = findProductByName(a.productName);
            if (p != null) p.quantity = a.prevQuantity;
            for (int i = sales.size() - 1; i >= 0; i--) {
                Sale s = sales.get(i);
                if (s.productName.equals(a.productName) && s.quantitySold == a.qty && s.unitPrice == a.unitPrice) { sales.remove(i); printSuccess("Undo: removed last sale entry for '" + a.productName + "'."); return; }
            }
            printInfo("Undo: sale reverted logically (stock restored).");
        } else { printError("Undo: unknown action type."); }

        // Sort inventory after undo
        inventory.sort(Comparator.comparing(prod -> prod.name.toLowerCase()));
    }

    public static Product findProductByName(String name) { for (Product p : inventory) if (p.name.equalsIgnoreCase(name)) return p; return null; }

    // ------------------------- REPORTS -------------------------
    public static void viewInventory() {
        printHeading("\n--- CURRENT INVENTORY ---");
        if (inventory.isEmpty()) { printInfo("Inventory is empty."); return; }
        for (Product p : inventory) System.out.println(p.name + " | Qty: " + p.quantity + " | Cost: " + p.cost + " | Selling Price: " + p.sellingPrice);
    }

    public static void printInventoryReport() {
        printHeading("\n=== CURRENT INVENTORY REPORT ===");
        if (inventory.isEmpty()) { printInfo("No inventory to report."); return; }
        for (Product p : inventory) System.out.println(p.name + " | Qty: " + p.quantity + " | Cost: " + p.cost + " | Selling Price: " + p.sellingPrice);
    }

    public static void printSalesReport() {
        printHeading("\n=== SALES REPORT ===");
        if (sales.isEmpty()) { printInfo("No sales recorded."); return; }
        double total = 0;
        for (Sale s : sales) { double subtotal = s.quantitySold * s.unitPrice; total += subtotal; System.out.println(s.productName + " | Qty Sold: " + s.quantitySold + " | Unit Price: " + s.unitPrice + " | Subtotal: " + subtotal); }
        printSuccess("TOTAL SALES: " + total);
    }

    // ------------------------- PERSISTENCE -------------------------
    public static void saveInventoryToFile() {
        try { PrintWriter writer = new PrintWriter(folder + "inventory.txt"); for (Product p : inventory) writer.println(p.name + "," + p.quantity + "," + p.cost + "," + p.sellingPrice); writer.close(); }
        catch (Exception e) { printError("Error saving inventory: " + e.getMessage()); }
    }

    public static void loadInventoryFromFile() {
        try {
            File file = new File(folder + "inventory.txt");
            if (!file.exists()) return;
            Scanner fileReader = new Scanner(file);
            while (fileReader.hasNextLine()) {
                String line = fileReader.nextLine();
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split(",");
                if (parts.length < 4) continue;
                inventory.add(new Product(parts[0], Integer.parseInt(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3])));
            }
            fileReader.close();
            // Sort after load
            inventory.sort(Comparator.comparing(prod -> prod.name.toLowerCase()));
        } catch (Exception e) { printError("Error loading inventory: " + e.getMessage()); }
    }
}
