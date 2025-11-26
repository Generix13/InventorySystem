// InventorySystem.java
// Version: 1.7.9b
// Last Updated: 2025-11-25
// Revision notes:
//  - Sales report now date-filtered (M/d or M/d/yyyy, current year default)
//  - Date-filtered sales report moved under View/Export Reports
//  - Removed redundant plain sales report
//  - Everything else unchanged

import java.util.*;
import java.io.*;
import java.text.*;

public class InventorySystem {

    public static final String VERSION = "1.7.9b";

    // ------------------------- DATA CLASSES -------------------------
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
        public double cost;
        public double sellingPrice;
        public double prevSellingPrice;
        public int prevQuantity;
        public double prevCost;
        public double unitPrice;

        public Action(String type, String productName, int qty) {
            this.type = type;
            this.productName = productName;
            this.qty = qty;
            this.createdNew = false;
            this.cost = 0.0;
            this.sellingPrice = 0.0;
            this.prevSellingPrice = 0.0;
            this.prevQuantity = 0;
            this.prevCost = 0.0;
            this.unitPrice = 0.0;
        }
    }

    public static class StoreProfile {
        public String name = "";
        public String owner = "";
        public String address = "";
        public String contact = "";
        public String tin = "";
    }

    // ------------------------- GLOBAL VARIABLES -------------------------
    public static ArrayList<Product> inventory = new ArrayList<>();
    public static ArrayList<Sale> sales = new ArrayList<>();
    public static ArrayList<Action> actionHistory = new ArrayList<>();
    public static ArrayList<Action> redoHistory = new ArrayList<>();
    public static Scanner input = new Scanner(System.in);

    public static String folder = System.getProperty("user.dir") + File.separator;

    public static StoreProfile profile = new StoreProfile();

    // ------------------------- COLOR HELPERS -------------------------
    public static void printSuccess(String msg) { System.out.println("\u001B[32m" + msg + "\u001B[0m"); }
    public static void printError(String msg) { System.out.println("\u001B[31m" + msg + "\u001B[0m"); }
    public static void printInfo(String msg) { System.out.println("\u001B[33m" + msg + "\u001B[0m"); }
    public static void printPrompt(String msg) { System.out.print("\u001B[36m" + msg + "\u001B[0m"); }
    public static void printHeading(String msg) { System.out.println("\u001B[34;1m" + msg + "\u001B[0m"); }

    // ------------------------- CLEAR CONSOLE -------------------------
    public static void clearConsole() {
        try {
            if (System.getProperty("os.name").toLowerCase().contains("windows"))
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // ignore clearing failure
        }
    }

    // ------------------------- MAIN -------------------------
    public static void main(String[] args) {
        clearConsole();
        printHeading("Inventory System - Version " + VERSION);

        loadProfile();
        loadInventoryFromFile();
        printSuccess("Inventory and profile loaded successfully.");

        while (true) {
            clearConsole();
            printMainMenu();
            printPrompt("Choose an option (1-6 or X to exit): ");
            String choice = input.nextLine().trim().toUpperCase();
            switch(choice) {
                case "1": addInventoryFlow(); break;
                case "2": recordSaleFlow(); break;
                case "3": viewReportMenu(); break;
                case "4": undoLastAction(); break;
                case "5": redoLastAction(); break;
                case "6": addEditProfileFlow(); break;
                case "X":
                    saveInventoryToFile();
                    saveProfile();
                    printSuccess("Inventory and profile saved. Exiting system...");
                    return;
                default:
                    printError("Invalid option. Try again.");
            }
        }
    }

    // ------------------------- MAIN MENU -------------------------
    public static void printMainMenu() {
        printHeading("\n=== SARI-SARI STORE INVENTORY SYSTEM ===");
        System.out.println("1. Add Purchased Inventory");
        System.out.println("2. Record Sales");
        System.out.println("3. View/Export Reports");
        System.out.println("4. UNDO last action");
        System.out.println("5. REDO last undone action");
        System.out.println("6. Add/Edit Store Profile");
        System.out.println("X. Exit");
    }

    // ------------------------- PROFILE -------------------------
    public static void addEditProfileFlow() {
        clearConsole();
        printHeading("\n--- Add/Edit/View Store Profile ---");
        if(!profile.name.isEmpty()) {
            System.out.println("Current Profile:");
            System.out.println("Store: " + profile.name);
            System.out.println("Owner: " + profile.owner);
            System.out.println("Address: " + profile.address);
            System.out.println("Contact: " + profile.contact);
            System.out.println("TIN: " + profile.tin);
            printPrompt("\nDo you want to EDIT this profile? (y/n): ");
            String choice = input.nextLine().trim().toLowerCase();
            if(!choice.equals("y")) return;
        }

        printPrompt("Store Name: "); profile.name = input.nextLine().trim();
        printPrompt("Owner Name: "); profile.owner = input.nextLine().trim();
        printPrompt("Address: "); profile.address = input.nextLine().trim();
        printPrompt("Contact Number: "); profile.contact = input.nextLine().trim();
        printPrompt("TIN Number: "); profile.tin = input.nextLine().trim();

        saveProfile();
        printSuccess("Profile saved successfully.");
        printPrompt("Press ENTER to continue...");
        input.nextLine();
    }

    public static void saveProfile() {
        try(PrintWriter writer = new PrintWriter(folder + "profile.txt")) {
            writer.println(profile.name);
            writer.println(profile.owner);
            writer.println(profile.address);
            writer.println(profile.contact);
            writer.println(profile.tin);
        } catch(Exception e) { printError("Error saving profile: " + e.getMessage()); }
    }

    public static void loadProfile() {
        try {
            File file = new File(folder + "profile.txt");
            if(!file.exists()) return;
            Scanner reader = new Scanner(file);
            if(reader.hasNextLine()) profile.name = reader.nextLine();
            if(reader.hasNextLine()) profile.owner = reader.nextLine();
            if(reader.hasNextLine()) profile.address = reader.nextLine();
            if(reader.hasNextLine()) profile.contact = reader.nextLine();
            if(reader.hasNextLine()) profile.tin = reader.nextLine();
            reader.close();
        } catch(Exception e) { printError("Error loading profile: " + e.getMessage()); }
    }

    // ------------------------- REPORT MENU -------------------------
    public static void viewReportMenu() {
        clearConsole();
        while(true){
            clearConsole();
            printHeading("\n--- VIEW / EXPORT REPORT ---");
            System.out.println("1. View Inventory Report");
            System.out.println("2. View Sales Report (Date Filtered)");
            System.out.println("3. Export Inventory Report to CSV");
            System.out.println("4. Export Sales Report to CSV");
            System.out.println("X. Back to Main Menu");
            printPrompt("Choose an option: ");
            String choice = input.nextLine().trim().toUpperCase();
            switch(choice){
                case "1": showInventoryReport(); break;
                case "2": showDateFilteredSalesReport(); break;
                case "3": exportInventoryReportCSV(); break;
                case "4": exportSalesReportCSV(); break;
                case "X": return;
                default: printError("Invalid option. Try again."); break;
            }
        }
    }

    // ------------------------- INVENTORY & SALES REPORT -------------------------
    public static void showInventoryReport() {
        clearConsole();
        StringBuilder sb = new StringBuilder();
        sb.append(generateReportHeader("INVENTORY REPORT"));
        if(inventory.isEmpty()) sb.append("No inventory to report.\n");
        else {
            sb.append(String.format("%-30s | %10s | %10s | %10s%n", "PRODUCT", "QTY", "COST", "SELLING"));
            sb.append("---------------------------------------------------------------------\n");
            for(Product p : inventory){
                String name = p.name.length() > 30 ? p.name.substring(0, 30) : p.name;
                sb.append(String.format("%-30s | %10d | %10.2f | %10.2f%n", name, p.quantity, p.cost, p.sellingPrice));
            }
        }
        System.out.println(sb.toString());
        printPrompt("Press ENTER to continue...");
        input.nextLine();
    }

    public static String generateReportHeader(String title) {
        StringBuilder sb = new StringBuilder();
        sb.append("STORE: ").append(profile.name).append("\n");
        sb.append("OWNER: ").append(profile.owner).append("\n");
        sb.append("ADDRESS: ").append(profile.address).append("\n");
        sb.append("CONTACT: ").append(profile.contact).append("\n");
        sb.append("TIN: ").append(profile.tin).append("\n");
        sb.append("REPORT: ").append(title).append("\n");
        sb.append("DATE/TIME: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("---------------------------------------------------------------------\n");
        return sb.toString();
    }

    // ------------------------- EXPORT CSV -------------------------
    public static void exportInventoryReportCSV() {
        clearConsole();
        try {
            String fileName = folder + "InventoryReport_" + System.currentTimeMillis() + ".csv";
            PrintWriter writer = new PrintWriter(fileName);
            writer.println("PRODUCT,QTY,COST,SELLING");
            for(Product p: inventory){
                writer.printf("%s,%d,%.2f,%.2f%n", p.name, p.quantity, p.cost, p.sellingPrice);
            }
            writer.close();
            printSuccess("Inventory CSV exported to: " + fileName);
            printPrompt("Press ENTER to continue...");
            input.nextLine();
        } catch(Exception e) { printError("Export error: "+e.getMessage()); }
    }

    public static void exportSalesReportCSV() {
        clearConsole();
        try {
            String fileName = folder + "SalesReport_" + System.currentTimeMillis() + ".csv";
            PrintWriter writer = new PrintWriter(fileName);
            writer.println("PRODUCT,QTY,UNIT PRICE,SUBTOTAL");
            for(Sale s: sales){
                double subtotal = s.quantitySold * s.unitPrice;
                writer.printf("%s,%d,%.2f,%.2f%n", s.productName, s.quantitySold, s.unitPrice, subtotal);
            }
            writer.close();
            printSuccess("Sales CSV exported to: " + fileName);
            printPrompt("Press ENTER to continue...");
            input.nextLine();
        } catch(Exception e) { printError("Export error: "+e.getMessage()); }
    }

    // ------------------------- ADD INVENTORY -------------------------
    public static void addInventoryFlow() {
        clearConsole();
        printHeading("\n--- Add Purchased Inventory ---");

        // Get product name with auto-suggest
        List<String> productNames = new ArrayList<>();
        for(Product p: inventory) productNames.add(p.name);
        String name = autoSuggestInput("Enter product name (or CANCEL): ", productNames);
        if(name == null) return;

        Integer qty = readIntOrCancel("Enter quantity bought (or CANCEL): ");
        if(qty == null) return;
        Double cost = readDoubleOrCancel("Enter cost per unit (or CANCEL): ");
        if(cost == null) return;
        Double sellingPrice = readDoubleOrCancel("Enter selling price per unit (or CANCEL, 0 to leave unset): ");
        if(sellingPrice == null) return;

        Product p = findProductByName(name);
        Action act = new Action("ADD", name, qty);
        if(p != null){
            act.createdNew = false;
            act.prevQuantity = p.quantity;
            act.prevCost = p.cost;
            act.prevSellingPrice = p.sellingPrice;
            p.quantity += qty;
            p.cost = cost;
            if(sellingPrice>0) p.sellingPrice = sellingPrice;
        } else {
            p = new Product(name, qty, cost, sellingPrice);
            inventory.add(p);
            act.createdNew = true;
            act.cost = cost;
            act.sellingPrice = sellingPrice;
        }
        actionHistory.add(act);
        redoHistory.clear();
        inventory.sort(Comparator.comparing(pr->pr.name.toLowerCase()));
        saveInventoryToFile();
        printSuccess("Inventory updated.");
        printPrompt("Press ENTER to continue...");
        input.nextLine();
    }

    // ------------------------- RECORD SALES -------------------------
    public static void recordSaleFlow(){
        clearConsole();
        printHeading("\n--- Record Sale ---");

        List<String> productNames = new ArrayList<>();
        for(Product p: inventory) productNames.add(p.name);
        String name = autoSuggestInput("Enter product name (or CANCEL): ", productNames);
        if(name == null) return;

        Product p = findProductByName(name);
        if(p==null){ printError("Product not found."); return; }
        if(p.sellingPrice <= 0){
            Double sp = readDoubleOrCancel("No selling price set. Enter selling price per unit (or CANCEL): ");
            if(sp==null) return;
            p.sellingPrice = sp;
        }
        Integer qty = readIntOrCancel("Enter quantity sold (or CANCEL): ");
        if(qty==null) return;
        if(qty>p.quantity){ printError("Not enough stock."); return; }

        commitSale(p, qty, p.sellingPrice);
        printSuccess("Sale recorded.");
        printPrompt("Press ENTER to continue...");
        input.nextLine();
    }

    public static void commitSale(Product product, int qty, double unitPrice){
        redoHistory.clear();
        int beforeQty = product.quantity;
        product.quantity -= qty;
        Sale sale = new Sale(product.name, qty, unitPrice);
        sales.add(sale);

        Action act = new Action("SALE", product.name, qty);
        act.prevQuantity = beforeQty;
        act.unitPrice = unitPrice;
        actionHistory.add(act);

        saveInventoryToFile();
        logSale(sale, "SALE");
    }

    // ------------------------- UNDO / REDO -------------------------
    public static void undoLastAction(){
        if(actionHistory.isEmpty()){ printInfo("Nothing to undo."); return; }
        Action a = actionHistory.remove(actionHistory.size()-1);
        if(a.type.equals("ADD")){
            Product p = findProductByName(a.productName);
            if(p==null){ printError("Undo: product not found."); return; }
            if(a.createdNew) inventory.remove(p);
            else { p.quantity=a.prevQuantity; p.cost=a.prevCost; p.sellingPrice=a.prevSellingPrice; }
        } else if(a.type.equals("SALE")){
            Product p = findProductByName(a.productName);
            if(p!=null) p.quantity = a.prevQuantity;
            for(int i=sales.size()-1;i>=0;i--){
                Sale s = sales.get(i);
                if(s.productName.equals(a.productName) && s.quantitySold==a.qty && s.unitPrice==a.unitPrice){
                    sales.remove(i); break;
                }
            }
            // optional: log UNDO to sales_log.txt if you want auditing of undos
            // logSale(new Sale(a.productName, a.qty, a.unitPrice), "UNDO");
        }
        redoHistory.add(a);
        saveInventoryToFile();
        printSuccess("Last action undone.");
        printPrompt("Press ENTER to continue...");
        input.nextLine();
    }

    public static void redoLastAction(){
        if(redoHistory.isEmpty()){ printInfo("Nothing to redo."); return; }
        Action a = redoHistory.remove(redoHistory.size()-1);
        if(a.type.equals("ADD")){
            Product p = findProductByName(a.productName);
            if(p!=null){ p.quantity+=a.qty; if(a.sellingPrice>0) p.sellingPrice=a.sellingPrice; p.cost=a.cost; }
            else inventory.add(new Product(a.productName, a.qty, a.cost, a.sellingPrice));
            actionHistory.add(a);
        } else if(a.type.equals("SALE")){
            Product p = findProductByName(a.productName);
            if(p!=null && a.qty<=p.quantity){
                p.quantity -= a.qty;
                Sale sale = new Sale(a.productName, a.qty, a.unitPrice);
                sales.add(sale);
                actionHistory.add(a);
                logSale(sale, "SALE");
            } else { printError("Redo failed: insufficient stock."); redoHistory.add(a); return; }
        }
        saveInventoryToFile();
        printSuccess("Last undone action redone.");
        printPrompt("Press ENTER to continue...");
        input.nextLine();
    }

    // ------------------------- SALES LOG -------------------------
    public static void logSale(Sale sale, String actionType){
        try(PrintWriter writer = new PrintWriter(new FileOutputStream(folder + "sales_log.txt", true))){
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            double subtotal = sale.quantitySold * sale.unitPrice;
            writer.printf("%s | %s | QTY: %d | UNIT PRICE: %.2f | SUBTOTAL: %.2f | ACTION: %s%n",
                          timestamp, sale.productName, sale.quantitySold, sale.unitPrice, subtotal, actionType);
        } catch(Exception e) {
            printError("Error logging sale: " + e.getMessage());
        }
    }

    // ------------------------- DATE-FILTERED SALES REPORT -------------------------
    public static void showDateFilteredSalesReport() {
        clearConsole();
        printHeading("--- SALES REPORT (DATE FILTERED) ---");

        Calendar cal = Calendar.getInstance();
        int currentYear = cal.get(Calendar.YEAR);

        Date startDate = null, endDate = null;
        SimpleDateFormat sdfFull = new SimpleDateFormat("yyyy-MM-dd");
        SimpleDateFormat sdfInputWithYear = new SimpleDateFormat("M/d/yyyy");
        SimpleDateFormat sdfLog = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            printPrompt("Enter start date (M/d or M/d/yyyy) [leave blank for all]: ");
            String startInput = input.nextLine().trim();
            if(!startInput.isEmpty()){
                if(startInput.matches("\\d{1,2}/\\d{1,2}")) startInput += "/" + currentYear;
                startDate = sdfInputWithYear.parse(startInput);
                // normalize startDate to 00:00:00
                Calendar c = Calendar.getInstance();
                c.setTime(startDate);
                c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND,0);
                startDate = c.getTime();
            }

            printPrompt("Enter end date (M/d or M/d/yyyy) [leave blank for all]: ");
            String endInput = input.nextLine().trim();
            if(!endInput.isEmpty()){
                if(endInput.matches("\\d{1,2}/\\d{1,2}")) endInput += "/" + currentYear;
                endDate = sdfInputWithYear.parse(endInput);
                // normalize endDate to 23:59:59
                Calendar c2 = Calendar.getInstance();
                c2.setTime(endDate);
                c2.set(Calendar.HOUR_OF_DAY, 23); c2.set(Calendar.MINUTE, 59); c2.set(Calendar.SECOND, 59); c2.set(Calendar.MILLISECOND,999);
                endDate = c2.getTime();
            }
        } catch(ParseException e){
            printError("Invalid date format. Press ENTER to return...");
            input.nextLine();
            return;
        }

        double total = 0;
        List<String> linesToShow = new ArrayList<>();
        File logFile = new File(folder + "sales_log.txt");
        if(!logFile.exists()){
            printError("No sales_log.txt found. Press ENTER to continue...");
            input.nextLine();
            return;
        }

        try(BufferedReader br = new BufferedReader(new FileReader(logFile))){
            String line;
            while((line = br.readLine()) != null){
                if(line.trim().isEmpty()) continue;
                String[] parts = line.split("\\|");
                if(parts.length < 6) continue;
                String timestampStr = parts[0].trim();
                Date saleDate;
                try {
                    saleDate = sdfLog.parse(timestampStr);
                } catch(ParseException pe) { continue; }

                boolean inRange = (startDate==null || !saleDate.before(startDate)) && (endDate==null || !saleDate.after(endDate));
                if(!inRange) continue;

                String productName = parts[1].trim();
                String qtyPart = parts[2].trim(); // "QTY: N"
                String unitPart = parts[3].trim(); // "UNIT PRICE: N"
                String subtotalPart = parts[4].trim(); // "SUBTOTAL: N"

                int qty = Integer.parseInt(qtyPart.replace("QTY:", "").trim());
                double unitPrice = Double.parseDouble(unitPart.replace("UNIT PRICE:", "").trim());
                double subtotal = Double.parseDouble(subtotalPart.replace("SUBTOTAL:", "").trim());
                total += subtotal;

                linesToShow.add(String.format("%-30s | %10d | %10.2f | %10.2f", productName, qty, unitPrice, subtotal));
            }
        } catch(Exception e) {
            printError("Error reading sales log: " + e.getMessage());
            input.nextLine();
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(generateReportHeader("SALES REPORT"));
        sb.append("DATE RANGE: ");
        sb.append(startDate != null ? sdfFull.format(startDate) : "ALL");
        sb.append(" to ");
        sb.append(endDate != null ? sdfFull.format(endDate) : "ALL");
        sb.append("\n");
        sb.append("---------------------------------------------------------------------\n");
        sb.append(String.format("%-30s | %10s | %10s | %10s%n", "PRODUCT", "QTY", "UNIT PRICE", "SUBTOTAL"));
        sb.append("---------------------------------------------------------------------\n");
        for(String l : linesToShow) sb.append(l).append("\n");
        sb.append("---------------------------------------------------------------------\n");
        sb.append(String.format("TOTAL SALES: %.2f%n", total));
        sb.append("---------------------------------------------------------------------\n");

        System.out.println(sb.toString());
        printPrompt("Press ENTER to continue...");
        input.nextLine();
    }

    // ------------------------- HELPERS -------------------------
    public static Product findProductByName(String name){
        for(Product p: inventory) if(p.name.equalsIgnoreCase(name)) return p;
        return null;
    }

    public static Integer readIntOrCancel(String prompt){
        while(true){
            printPrompt(prompt);
            String s = input.nextLine().trim();
            if(s.equalsIgnoreCase("CANCEL")) return null;
            try{ return Integer.parseInt(s); } catch(Exception e){ printError("Invalid number."); }
        }
    }

    public static Double readDoubleOrCancel(String prompt){
        while(true){
            printPrompt(prompt);
            String s = input.nextLine().trim();
            if(s.equalsIgnoreCase("CANCEL")) return null;
            try{ return Double.parseDouble(s); } catch(Exception e){ printError("Invalid number."); }
        }
    }

    // ------------------------- AUTO-SUGGEST INPUT -------------------------
    public static String autoSuggestInput(String prompt, List<String> options) {
        while (true) {
            printPrompt(prompt);
            String inputStr = input.nextLine().trim();
            if (inputStr.equalsIgnoreCase("CANCEL")) return null;

            List<String> matches = new ArrayList<>();
            for (String opt : options) {
                if (opt.toLowerCase().contains(inputStr.toLowerCase())) matches.add(opt);
            }

            if (matches.isEmpty()) {
                printInfo("No matches found. Try again or type CANCEL.");
                continue;
            } else if (matches.size() == 1) {
                printInfo("Selected: " + matches.get(0));
                printPrompt("Press ENTER to confirm or type CANCEL to cancel: ");
                String confirm = input.nextLine().trim();
                if (confirm.equalsIgnoreCase("CANCEL")) return null;
                return matches.get(0);
            } else {
                printInfo("Multiple matches found:");
                for (int i = 0; i < matches.size(); i++) System.out.println((i+1) + ". " + matches.get(i));
                printPrompt("Choose number or type CANCEL: ");
                String choice = input.nextLine().trim();
                if (choice.equalsIgnoreCase("CANCEL")) return null;

                try {
                    int idx = Integer.parseInt(choice);
                    if (idx >= 1 && idx <= matches.size()) return matches.get(idx - 1);
                    else printError("Invalid number selection.");
                } catch (Exception e) {
                    printError("Invalid input. Enter a number or CANCEL.");
                }
            }
        }
    }

    // ------------------------- LOAD/SAVE INVENTORY -------------------------
    public static void saveInventoryToFile() {
        try(PrintWriter writer = new PrintWriter(folder + "inventory.txt")){
            for(Product p: inventory){
                writer.printf("%s,%d,%.2f,%.2f%n", p.name, p.quantity, p.cost, p.sellingPrice);
            }
        } catch(Exception e){ printError("Error saving inventory: "+e.getMessage()); }
    }

    public static void loadInventoryFromFile() {
        try{
            File file = new File(folder + "inventory.txt");
            if(!file.exists()) return;
            Scanner reader = new Scanner(file);
            while(reader.hasNextLine()){
                String line = reader.nextLine();
                String[] parts = line.split(",");
                if(parts.length==4){
                    String name = parts[0];
                    int qty = Integer.parseInt(parts[1]);
                    double cost = Double.parseDouble(parts[2]);
                    double selling = Double.parseDouble(parts[3]);
                    inventory.add(new Product(name, qty, cost, selling));
                }
            }
            reader.close();
            inventory.sort(Comparator.comparing(pr->pr.name.toLowerCase()));
        } catch(Exception e){ printError("Error loading inventory: "+e.getMessage()); }
    }

}
