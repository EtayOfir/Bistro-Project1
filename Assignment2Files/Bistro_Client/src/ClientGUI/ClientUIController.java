package ClientGUI;

import java.io.IOException;

import client.ChatClient;
import common.ChatIF;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.AnchorPane;

public class ClientUIController implements ChatIF {

    // UI Components

    @FXML
    private TextField orderNumberInput;

    @FXML
    private Label orderNumber;   // we still show current order number as a label

    @FXML
    private TextField numberOfGuestsField;   // editable guests

    @FXML
    private TextField orderDateField;        // editable date

    @FXML
    private Button showReservation;

    @FXML
    private Button update;

    @FXML
    private Button exit;

    // NEW: table for reservation details
    @FXML
    private TableView<ReservationRow> reservationTable;

    @FXML
    private TableColumn<ReservationRow, String> fieldColumn;

    @FXML
    private TableColumn<ReservationRow, String> valueColumn;

    // Keep this as a log/status area
    @FXML
    private TextArea reservationDetailsTextArea;

    private String orderNum;

    @FXML
    private AnchorPane blurOverlay;
    
    // Client
    private ChatClient chatClient;

    // NEW: setup table columns
    @FXML
    public void initialize() {
        if (fieldColumn != null) {
            fieldColumn.setCellValueFactory(data -> data.getValue().fieldProperty());
        }
        if (valueColumn != null) {
            valueColumn.setCellValueFactory(data -> data.getValue().valueProperty());
        }
        if (blurOverlay != null) {
            blurOverlay.setEffect(new GaussianBlur(20));
        }
    }

    public void initClient(String host, int port) {
        try {
            this.chatClient = new ChatClient(host, port, this); // "this" is ChatIF
        } catch (IOException e) {
            e.printStackTrace();
            display("Could not connect to server: " + e.getMessage());
        }
    }

    public void setChatClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @FXML
    private void onUpdateReservationClicked(ActionEvent event) {
        if (chatClient == null) {
            display("Client not initialized.");
            return;
        }

        // Must already have a reservation loaded
        String currentOrderNum = orderNumber.getText();
        if (currentOrderNum == null || currentOrderNum.isBlank()) {
            display("No reservation loaded to update.");
            return;
        }

        String newGuests = numberOfGuestsField.getText();
        String newDate   = orderDateField.getText();

        if (newGuests == null || newGuests.isBlank() ||
            newDate == null   || newDate.isBlank()) {
            display("Please enter both number of guests and order date (yyyy-MM-dd).");
            return;
        }

        // Optional: basic validation for number of guests
        try {
            Integer.parseInt(newGuests);
        } catch (NumberFormatException e) {
            display("Number of guests must be an integer.");
            return;
        }

        // Build update message:
        //   #UPDATE_RESERVATION <orderNum> <numGuests> <orderDate>
        String msg = "#UPDATE_RESERVATION " + currentOrderNum + " " + newGuests + " " + newDate;

        reservationDetailsTextArea.appendText("\nUpdating reservation...\n");
        chatClient.handleMessageFromClientUI(msg);
    }

    // This method is called when the "Show Reservation" button is pressed
    @FXML
    private void onShowReservationClicked(ActionEvent event) {
        if (chatClient == null) {
            display("Client not initialized.");
            return;
        }

        String orderNumToSearch = orderNumberInput.getText();

        if (orderNumToSearch == null || orderNumToSearch.isBlank()) {
            display("Please enter an order number.");
            return;
        }

        // Trim spaces and send request to server
        SearchOrderNum(orderNumToSearch.trim());
    }

    // Send message to server through ChatClient
    public void SearchOrderNum(String orderNum) {
        this.orderNum = orderNum;
        try {
            String msg = "#GET_RESERVATION " + orderNum;
            chatClient.handleMessageFromClientUI(msg);

            Platform.runLater(() -> {
                reservationDetailsTextArea.setText("Loading reservation " + orderNum + "...\n");
                if (reservationTable != null) {
                    reservationTable.getItems().clear();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                orderNumber.setText("Error");
                numberOfGuestsField.setText("-");
                orderDateField.setText("-");
                reservationDetailsTextArea.setText("Error sending request: " + e.getMessage() + "\n");
                if (reservationTable != null) {
                    reservationTable.getItems().clear();
                }
            });
        }
    }

    /**
     * Called by ChatClient whenever a message comes from the server
     */
    @Override
    public void display(String message) {
        System.out.println("UI display(): " + message);

        Platform.runLater(() -> handleServerMessage(message));
    }

    // Parse server message and update fields + table + log
    private void handleServerMessage(String message) {
        if (message == null) return;

        if (message.startsWith("RESERVATION|")) {
            // Format from server:
            // RESERVATION|orderNum|numGuests|orderDate|confCode|subscriberId|placingDate
            String[] parts = message.split("\\|");
            if (parts.length >= 7) {
                String ordNum       = parts[1];
                String numGuests    = parts[2];
                String date         = parts[3];
                String confCode     = parts[4];
                String subscriberId = parts[5];
                String placingDate  = parts[6];

                // remember last order
                this.orderNum = ordNum;

                // Update UI fields
                orderNumber.setText(ordNum);
                numberOfGuestsField.setText(numGuests);
                orderDateField.setText(date);

                // Build table rows
                ObservableList<ReservationRow> rows = FXCollections.observableArrayList(
                        new ReservationRow("Order Number",      ordNum),
                        new ReservationRow("Guests",            numGuests),
                        new ReservationRow("Order Date",        date),
                        new ReservationRow("Confirmation Code", confCode),
                        new ReservationRow("Subscriber ID",     subscriberId),
                        new ReservationRow("Placing Date",      placingDate)
                );
                if (reservationTable != null) {
                    reservationTable.setItems(rows);
                }

                reservationDetailsTextArea.setText("Reservation loaded successfully.\n");

            } else {
                orderNumber.setText("Error");
                numberOfGuestsField.setText("-");
                orderDateField.setText("-");
                if (reservationTable != null) {
                    reservationTable.getItems().clear();
                }
                reservationDetailsTextArea.setText("Invalid reservation data from server: " + message + "\n");
            }

        } else if (message.startsWith("RESERVATION_NOT_FOUND")) {
            orderNumber.setText("Not found");
            numberOfGuestsField.setText("-");
            orderDateField.setText("-");
            if (reservationTable != null) {
                reservationTable.getItems().clear();
            }

            String ord = (orderNum != null) ? orderNum : "";
            reservationDetailsTextArea.setText(
                    "Reservation not found for order number: " + ord + "\n");

        } else if (message.equals("Connected to server") || message.equals("Disconnected from server")) {
            reservationDetailsTextArea.appendText(message + "\n");
        } else {
            reservationDetailsTextArea.appendText(message + "\n");
        }
    }

    @FXML
    private void onExitClicked(ActionEvent event) {
        try {
            if (chatClient != null) {
                chatClient.closeConnection();
            }
        } catch (Exception e) {
            // ignore errors on exit
        }

        Platform.exit();   // closes the JavaFX UI
        System.exit(0);    // kills the process completely
    }

    // --- NEW helper model class for table rows ---
    public static class ReservationRow {
        private final StringProperty field  = new SimpleStringProperty();
        private final StringProperty value  = new SimpleStringProperty();

        public ReservationRow(String field, String value) {
            this.field.set(field);
            this.value.set(value);
        }

        public StringProperty fieldProperty() { return field; }
        public StringProperty valueProperty() { return value; }

        public String getField() { return field.get(); }
        public String getValue() { return value.get(); }

        public void setField(String f) { field.set(f); }
        public void setValue(String v) { value.set(v); }
    }
}
