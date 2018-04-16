package OrderManager;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import Database.Database;
import LiveMarketData.LiveMarketData;
import OrderClient.NewOrderSingle;
import OrderRouter.Router;
import TradeScreen.TradeScreen;

import static java.lang.StrictMath.abs;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;

public class OrderManager {
    private static LiveMarketData liveMarketData;
    private HashMap<Integer, Order> orders = new HashMap<>(); //debugger will do this line as it gives state to the object
    //currently recording the number of new order messages we get. TODO why? use it for more?
    private int id = 0; //debugger will do this line as it gives state to the object
    private Socket[] orderRouters; //debugger will skip these lines as they dissapear at compile time into 'the object'/stack
    private Socket[] clients;
    private Socket trader;
    private boolean tradeExecuting;
    private boolean finished;
    private int tradesCompleted;
    private int buysCompleted;
    private int sellsCompleted;

    //@param args the command line arguments
    public OrderManager(InetSocketAddress[] orderRouters, InetSocketAddress[] clients, InetSocketAddress trader, LiveMarketData liveMarketData) throws IOException, ClassNotFoundException, InterruptedException {
        this.liveMarketData = liveMarketData;
        this.trader = connect(trader);
        this.tradesCompleted = 0;
        //for the router connections, copy the input array into our object field.
        //but rather than taking the address we create a socket+ephemeral port and connect it to the address
        this.orderRouters = new Socket[orderRouters.length];
        int i = 0; //need a counter for the the output array
        for (InetSocketAddress location : orderRouters) {
            this.orderRouters[i] = connect(location);
            i++;
        }

        //repeat for the client connections
        this.clients = new Socket[clients.length];
        i = 0;
        for (InetSocketAddress location : clients) {
            this.clients[i] = connect(location);
            i++;
        }
        int clientId, routerId;
        Socket client, router;
        //main loop, wait for a message, then process it
        while (!finished) {
            //TODO this is pretty cpu intensive, use a more modern polling/interrupt/select approach
            //we want to use the arrayindex as the clientId, so use traditional for loop instead of foreach
            for (clientId = 0; clientId < this.clients.length; clientId++) { //check if we have data on any of the sockets
                client = this.clients[clientId];
                if (0 < client.getInputStream().available()) { //if we have part of a message ready to read, assuming this doesn't fragment messages
                    ObjectInputStream is = new ObjectInputStream(client.getInputStream()); //create an object inputstream, this is a pretty stupid way of doing it, why not create it once rather than every time around the loop
                    String method = (String) is.readObject();
//                    System.out.println(Thread.currentThread().getName() + " calling " + method);
                    switch (method) { //determine the type of message and process it
                        //call the newOrder message with the clientId and the message (clientMessageId,NewOrderSingle)
                        case "newOrderSingle":
                            newOrder(clientId, is.readInt(), (NewOrderSingle) is.readObject());
                            break;
                        case "tradeComplete":
                            tradeComplete();
                            //case "cancel":
                            //TODO create a default case which errors with "Unknown message type"+...
                    }
                }
            }
            for (routerId = 0; routerId < this.orderRouters.length; routerId++) { //check if we have data on any of the sockets
                router = this.orderRouters[routerId];
                if (0 < router.getInputStream().available()) { //if we have part of a message ready to read, assuming this doesn't fragment messages
                    ObjectInputStream is = new ObjectInputStream(router.getInputStream()); //create an object inputstream, this is a pretty stupid way of doing it, why not create it once rather than every time around the loop
                    String method = (String) is.readObject();
//                    System.out.println(Thread.currentThread().getName() + " calling " + method);
                    switch (method) { //determine the type of message and process it
                        case "bestPrice":
                            int OrderId = is.readInt();
                            int SliceId = is.readInt();
                            Order slice = orders.get(OrderId).slices.get(SliceId);
                            slice.bestPrices[routerId] = is.readDouble();
                            slice.bestPriceCount += 1;
                            if (slice.bestPriceCount == slice.bestPrices.length) {
                                reallyRouteOrder(SliceId, slice);
                            }
                            break;
                        case "newFill":
                            newFill(is.readInt(), is.readInt(), is.readInt(), is.readDouble());
                            break;
                    }
                }
            }

            if (0 < this.trader.getInputStream().available()) {
                ObjectInputStream is = new ObjectInputStream(this.trader.getInputStream());
                String method = (String) is.readObject();
//                System.out.println(Thread.currentThread().getName() + " calling " + method);
                switch (method) {
                    case "acceptOrder":
                        acceptOrder(is.readInt());
                        break;
                    case "sliceOrder":
                        sliceOrder(is.readInt(), is.readInt());
                        break;
                    case "updateOrder":
                        updateOrder(is.readInt(), (Order) is.readObject());
                        break;
                }
            }
        }
    }

    private void tradeComplete() throws IOException, InterruptedException {
        tradesCompleted++;
        if (!orders.isEmpty()) {
            for (Order order : orders.values()) {
                nextTrade(order.id, order);
            }
        } else {
            System.out.println("\nAll orders complete. End of trading day. \n"
                    + tradesCompleted + " trades completed today.\n"
                    + buysCompleted + " buy orders and " + sellsCompleted + " sell orders filled.");
            for (Socket r : orderRouters) {
                ObjectOutputStream os = new ObjectOutputStream(r.getOutputStream());
                os.writeObject(Router.api.terminateRouter);
            }
            finished = true;
        }
    }

    private void nextTrade(int id, Order o) throws IOException, InterruptedException {
        if (!tradeExecuting) {
            price(id, o);
            tradeExecuting = true;
        }
    }

    private Socket connect(InetSocketAddress location) throws InterruptedException {
        boolean connected = false;
        int tryCounter = 0;
        while (!connected && tryCounter < 600) {
            try {
                Socket s = new Socket(location.getHostName(), location.getPort());
                s.setKeepAlive(true);
                return s;
            } catch (IOException e) {
                sleep(1000);
                tryCounter++;
            }
        }
        System.out.println("Failed to connect to " + location.toString());
        return null;
    }

    private synchronized void newOrder(int clientId, int clientOrderId, NewOrderSingle nos) throws IOException, InterruptedException {
        orders.put(id, new Order(id, clientId, clientOrderId, nos.instrument, nos.size, nos.type));
        //send a message to the client with 39=A; //OrdStatus is Fix 39, 'A' is 'Pending New'
        ObjectOutputStream os = new ObjectOutputStream(clients[clientId].getOutputStream());
        //newOrderSingle acknowledgement
        //ClOrdId is 11=
        os.writeObject("11=" + clientOrderId + ";35=D;54="+nos.getFixTagOrderType()+";39=A;");
        os.flush();
        sendOrderToTrader(id, orders.get(id), TradeScreen.api.newOrder);
        //send the new order to the trading screen
        //don't do anything else with the order, as we are simulating high touch orders and so need to wait for the trader to accept the order
        id++;
    }

    private synchronized void sendOrderToTrader(int id, Order o, Object method) throws IOException, InterruptedException {
        ObjectOutputStream ost = new ObjectOutputStream(trader.getOutputStream());
        ost.writeObject(method);
        ost.writeInt(id);
        ost.writeObject(o);
        ost.flush();
    }

    public synchronized void acceptOrder(int id) throws IOException, InterruptedException {
        Order o = orders.get(id);
        if (o.OrdStatus != 'A') { //Pending New
            System.out.println("error accepting order that has already been accepted");
        } /** else statement */
        o.OrdStatus = '0'; //New
        ObjectOutputStream os = new ObjectOutputStream(clients[o.clientId].getOutputStream());
        os.writeObject("11=" + o.clientOrderID + ";35=D;54="+o.getFixTagOrderType()+";39=" + o.getOrdStatus());
        os.flush();

        nextTrade(id, o);
    }

    private void internalCross(int id, Order o) throws IOException, InterruptedException {
        for (Order order : orders.values()) {
            if (o.id != order.id) {
                Order matchingOrder = order;
                if (matchingOrder.instrument.toString().equals((o.instrument.toString()))) {
                    if (abs(matchingOrder.initialMarketPrice - o.initialMarketPrice) < 10) {
                        int sizeBefore = o.sizeRemaining();
                        o.cross(matchingOrder);
//                        if (sizeBefore != o.sizeRemaining()) {
                            //////////
                            System.out.println("sent 'cross' to trader");
                            //////////
                            sendOrderToTrader(id, o, TradeScreen.api.cross);
                            sendOrderToTrader(matchingOrder.id, matchingOrder, TradeScreen.api.cross);
                            break;
//                        }
                    }
                } else {
                    System.out.println("Instruments are different");
                    continue;
                }
            }
        }
    }

    private void cancelOrder() {
        /** implement in SampleClient */
    }

    private void deleteOrder(int id) throws IOException {
        orders.remove(id);
    }

    private synchronized void newFill(int id, int sliceId, int size, double price) throws
            IOException, InterruptedException {
        Order o = orders.get(id);
        Order slice = o.slices.get(sliceId);
        slice.createFill(size, price);
        if (slice.sizeRemaining() > 0) {
            slice.createFill(slice.sizeRemaining(), price);
        }
        if (o.sizeRemaining() <= 0) {
            o.setOrdStatus('2');
//            System.out.println("Completed OM order: " + o.id + " client " + (o.getClientId() + 1) + " client order id: " + o.clientOrderID);
            sendOrderToTrader(id, o, TradeScreen.api.fill);
        } else {
            o.setOrdStatus('1');
//            System.out.println("OM order: " + o.id + " client " + (orders.get(id).getClientId() + 1) + " client order id: " + (orders.get(id).clientOrderID) + " size: " + o.sizeRemaining() + "/" + o.size);
            sendOrderToTrader(id, o, TradeScreen.api.fill);
        }
    }

    private synchronized void updateOrder(int id, Order o) throws IOException, InterruptedException {
        if (orders.containsKey(id)) {
            ObjectOutputStream os = new ObjectOutputStream(clients[o.getClientId()].getOutputStream());
            os.writeObject("11=" + o.clientOrderID + ";35=D;54="+o.getFixTagOrderType()+";39=" + o.getOrderStatus());
            os.flush();
            if (o.getOrdStatus() == '2') {
                deleteOrder(id);
                if(o.getFixTagOrderType() == 1){
                    buysCompleted++;
                } else {
                    sellsCompleted++;
                }
                tradeExecuting = false;
            } else {
                Order order = randomOrder();
                id = order.id;
                price(id, order);
            }
        }
    }

    private Order randomOrder() {
        Random rand = new Random();
        int i = rand.nextInt(orders.size());
        Integer key = (Integer)orders.keySet().toArray()[i];
        return orders.get(key);
    }

    public void sliceOrder(int id, int sliceSize) throws IOException, InterruptedException {
        Order o = orders.get(id);
        //slice the order. We have to check this is a valid size.
        //Order has a list of slices, and a list of fills, each slice is a childorder and each fill is associated with either a child order or the original order
        if (sliceSize > o.sizeRemaining()) {
            sliceSize = o.sizeRemaining();
        }
        int sliceId = o.newSlice(sliceSize);
        o.slices.get(sliceId).setInitialPrice(o.initialMarketPrice);
        Order slice = o.slices.get(sliceId);
        //TODO: Make internal cross great again
//        if(orders.size()>1) {
//            internalCross(id, slice);
//        }
        int sizeRemaining = slice.sizeRemaining();
        if (sizeRemaining > 0) {
            routeOrder(id, sliceId, sizeRemaining, slice);
        } else {
            updateOrder(id, o);
        }
    }

    private synchronized void routeOrder(int id, int sliceId, int size, Order order) throws IOException {
        for (Socket r : orderRouters) {
            ObjectOutputStream os = new ObjectOutputStream(r.getOutputStream());
            os.writeObject(Router.api.priceAtSize);
            os.writeInt(id);
            os.writeInt(sliceId);
            os.writeObject(order.instrument);
            os.writeInt(size);
            os.flush();
        }
        //need to wait for these prices to come back before routing
        order.bestPrices = new double[orderRouters.length];
        order.bestPriceCount = 0;
    }

    private synchronized void reallyRouteOrder(int sliceId, Order order) throws IOException {
        //TODO this assumes we are buying rather than selling
        int index = 0;
        if(order.type == "buy") {
            double min = order.bestPrices[0];
            for (int i = 1; i < order.bestPrices.length; i++) {
                if (min > order.bestPrices[i]) {
                    index = i;
                    min = order.bestPrices[i];
                }
            }
        } else {
            double max = order.bestPrices[0];
            for(int j = 1; j < order.bestPrices.length; j++){
                if(max < order.bestPrices[j]){
                    index = j;
                    max = order.bestPrices[j];
                }
            }

        }
        ObjectOutputStream os = new ObjectOutputStream(orderRouters[index].getOutputStream());
        os.writeObject(Router.api.routeOrder);
        os.writeInt(order.id);
        os.writeInt(sliceId);
        os.writeInt(order.sizeRemaining());
        os.writeObject(order.instrument);
        os.flush();
    }

    private void sendCancel(Order order, Router orderRouter) {
        //orderRouter.sendCancel(order);
        //order.orderRouter.writeObject(order);
    }

    private synchronized void price(int id, Order o) throws IOException, InterruptedException {
        liveMarketData.setPrice(o);
        sendOrderToTrader(id, o, TradeScreen.api.price);
    }
}