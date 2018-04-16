import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.HashMap;

import javax.net.ServerSocketFactory;

import OrderManager.Order;
import TradeScreen.TradeScreen;

public class Trader extends Thread implements TradeScreen{
	private HashMap<Integer,Order> orders=new HashMap<Integer,Order>();
	private static Socket omConn;
	private int port;
	private boolean finished;
	Trader(String name,int port){
		this.setName(name);
		this.port=port;
	}
	ObjectInputStream  is;
	ObjectOutputStream os;
	public synchronized void run(){
		//OM will connect to us
		try {
			omConn=ServerSocketFactory.getDefault().createServerSocket(port).accept();

//			is=new ObjectInputStream( omConn.getInputStream());
			InputStream s=omConn.getInputStream(); //if i try to create an objectinputstream before we have data it will block
			while(!finished){ /** fix this */
				if(0<s.available()){
					//TODO check if we need to create each time. this will block if no data, but maybe we can still try to create it once instead of repeatedly
					is=new ObjectInputStream(s);
					api method=(api)is.readObject();
//					System.out.println(Thread.currentThread().getName()+" calling: "+method);
					switch(method){
						case newOrder:newOrder(is.readInt(),(Order)is.readObject());break;
						case price:price(is.readInt(),(Order)is.readObject());break;
						case cross:fill(is.readInt(),(Order)is.readObject());break; //TODO
						case fill:fill(is.readInt(), (Order) is.readObject());break; //TODO
					}
				}else{
					//System.out.println("Trader Waiting for data to be available - sleep 1s");
					Thread.sleep(1000);
				}
			}
		} catch (IOException | ClassNotFoundException | InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	@Override
	public synchronized void newOrder(int id,Order order) throws IOException, InterruptedException {
		//TODO the order should go in a visual grid, but not needed for test purposes
		orders.put(id, order);
		System.out.println(Thread.currentThread().getName()+ " received new " + order.type + " order with ID " + id + " from client " + (orders.get(id).getClientId()+1) + " order size remaining: [" + orders.get(id).sizeRemaining() + "/" +orders.get(id).size+"]");
		acceptOrder(id);
	}

	@Override
	public synchronized void acceptOrder(int id) throws IOException {
		os=new ObjectOutputStream(omConn.getOutputStream());
		os.writeObject("acceptOrder");
		os.writeInt(id);
		os.flush();
	}

	public synchronized void fill(int id, Order o) throws IOException, InterruptedException {
		orders.remove(id);
		orders.put(id, o);
        os=new ObjectOutputStream(omConn.getOutputStream());
		os.writeObject("updateOrder");
		os.writeInt(id);
		os.writeObject(o);
		os.flush();
		System.out.println(Thread.currentThread().getName()+ " filling order " + id + " from client " + (orders.get(id).getClientId()+1) + " order size remaining: [" + orders.get(id).sizeRemaining() + "/" +orders.get(id).size +"]");
		if (o.getOrderStatus() == '2') {
			orders.remove(id);
			if(orders.isEmpty()){
				finished=true;
			}
		}
	}

	@Override
	public synchronized void price(int id,Order o) throws InterruptedException, IOException {
		//TODO should update the trade screen
		if(o.sizeRemaining() < o.size/2){
            sliceOrder(id,orders.get(id).sizeRemaining());
        } else {
            sliceOrder(id,orders.get(id).sizeRemaining()/2);
        }

	}

	@Override
	public synchronized void sliceOrder(int id, int sliceSize) throws IOException {
		os=new ObjectOutputStream(omConn.getOutputStream());
		os.writeObject("sliceOrder");
		os.writeInt(id);
		os.writeInt(sliceSize);
		os.flush();
	}
}