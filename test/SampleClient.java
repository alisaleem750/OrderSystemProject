import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Random;

import OrderClient.Client;
import OrderClient.NewOrderSingle;
import OrderManager.Order;
import Ref.Instrument;
import Ref.Ric;

public class SampleClient extends Mock implements Client{
	private static final Random RANDOM_NUM_GENERATOR=new Random();
	private static final Instrument[] INSTRUMENTS={new Instrument(new Ric("VOD.L")), new Instrument(new Ric("BP.L")), new Instrument(new Ric("BT.L"))};
	private HashMap<Integer, NewOrderSingle> OUT_QUEUE=new HashMap(); //queue for outgoing orders
	private int id=0; //message id number
	private Socket omConn; //connection to order manager
	private boolean finished;

	public SampleClient(int port) throws IOException{
		//OM will connect to us
		omConn=new ServerSocket(port).accept();
		System.out.println("OM connected to client port "+port);
	}
	
	@Override
	public synchronized int sendOrder(Object orderType)throws IOException{
		int size=RANDOM_NUM_GENERATOR.nextInt(5000);
		/** Ali - below should be price, not instId */
		int instid=RANDOM_NUM_GENERATOR.nextInt(3);
		Instrument instrument=INSTRUMENTS[RANDOM_NUM_GENERATOR.nextInt(INSTRUMENTS.length)];
		NewOrderSingle nos=new NewOrderSingle(size,instid,instrument);
		//show("sendOrder: id="+id+" size="+size+" instrument="+INSTRUMENTS[instid].toString());
		show("sendOrder: id="+id+" size="+size+" instrument="+nos.instrument.toString());
		OUT_QUEUE.put(id,nos);
		if(omConn.isConnected()){
			ObjectOutputStream os=new ObjectOutputStream(omConn.getOutputStream());
			os.writeObject("newOrderSingle");
			os.writeInt(id);
			os.writeObject(nos);
			os.flush();
		}
		return id++;
	}

	@Override
	public void sendCancel(int idToCancel){
		show("sendCancel: id="+idToCancel);
		if(omConn.isConnected()){
			//OMconnection.sendMessage("cancel",idToCancel);
		}
	}

	@Override
	public void partialFill(Order order){show(""+order);
	}

	@Override
	public void fullyFilled(Order order){show(""+order);
		OUT_QUEUE.remove(order.clientOrderID);
	}

	@Override
	public void cancelled(Order order){show(""+order);
		OUT_QUEUE.remove(order.clientOrderID);
	}

	enum methods{newOrderSingleAcknowledgement, orderCompleteAcknowledgement, orderPartialFillAcknowledgement, dontKnow};
	@Override
	public synchronized void messageHandler() throws InterruptedException {
		
		ObjectInputStream is;
		try {
			while(!finished){
				//is.wait(); //this throws an exception!!
				while(0<omConn.getInputStream().available()){
					is = new ObjectInputStream(omConn.getInputStream());
					String fix=(String)is.readObject();
					System.out.println(Thread.currentThread().getName()+" received fix message: "+fix);
					String[] fixTags=fix.split(";");
					int OrderId=-1;
					char MsgType;
					int OrdStatus;
					methods whatToDo=methods.dontKnow;
					for(int i=0;i<fixTags.length;i++){
						String[] tag_value=fixTags[i].split("=");
						switch(tag_value[0]){
							case"11":OrderId=Integer.parseInt(tag_value[1]);break;
							case"35":MsgType=tag_value[1].charAt(0);
								if(MsgType=='A')whatToDo=methods.newOrderSingleAcknowledgement;
								break;
							case"54"://doSomethingWithOrderBuyOrSell?
							case"39":OrdStatus=tag_value[1].charAt(0);
								if(OrdStatus==('A'))whatToDo=methods.newOrderSingleAcknowledgement;
//								else if(OrdStatus=='1')whatToDo=methods.orderPartialFillAcknowledgement;
								else if(OrdStatus=='2')whatToDo=methods.orderCompleteAcknowledgement;
								break;
						}
					}
					switch(whatToDo){
						case newOrderSingleAcknowledgement:newOrderSingleAcknowledgement(OrderId); break;
						case orderCompleteAcknowledgement:orderCompleteAcknowledgement(OrderId); break;
						case orderPartialFillAcknowledgement:partialFill(OrderId); break;
						case dontKnow: doNothing();
					}
					
					/*message=connection.getMessage();
					char type;
					switch(type){
						case 'C':cancelled(message);break;
						case 'P':partialFill(message);break;
						case 'F':fullyFilled(message);
					}*/
					//show("");
				}
			}
		} catch (IOException|ClassNotFoundException e){
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private void doNothing() {
	}

	private void partialFill(int orderId) {
		System.out.println("Order " + orderId + " partially filled.");
	}

	private void orderCompleteAcknowledgement(int orderId) throws InterruptedException, IOException {
		System.out.println("Order " + orderId + " is complete. Removing from queue.");
		OUT_QUEUE.remove(orderId);
		if(OUT_QUEUE.isEmpty()){
			finished = true;
		}
		BackToOrderManager();
	}

	private void BackToOrderManager() throws IOException {
		ObjectOutputStream os = new ObjectOutputStream(omConn.getOutputStream());
		os.writeObject("tradeComplete");

	}

	private synchronized void newOrderSingleAcknowledgement(int OrderId){
		System.out.println(Thread.currentThread().getName()+" called newOrderSingleAcknowledgement");
		// Update the order status to new.
		NewOrderSingle nos = OUT_QUEUE.get(OrderId);
	}
/*listen for connections
once order manager has connected, then send and cancel orders randomly
listen for messages from order manager and print them to stdout.*/
}
