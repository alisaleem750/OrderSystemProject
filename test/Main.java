import java.io.IOException;
import java.net.InetSocketAddress;

import LiveMarketData.LiveMarketData;

public class Main{
	public static void main(String[] args) throws IOException{

		System.out.println("TEST: this program tests ordermanager");

		//start sample clients
		(new MockClient("Client 1",2000)).start();
		(new MockClient("Client 2",2001)).start();
		(new MockClient("Client 3",2002)).start();
		
		//start sample routers
		(new SampleRouter("Router LSE",2010)).start();
		(new SampleRouter("Router BATE",2011)).start();

		//start a trader
		(new Trader("Trader James",2020)).start();

		//start order manager

		InetSocketAddress clientSocketAddress1 = new InetSocketAddress("localhost",2000);
		InetSocketAddress clientSocketAddress2 = new InetSocketAddress("localhost",2001);
		InetSocketAddress clientSocketAddress3 = new InetSocketAddress("localhost",2002);
		InetSocketAddress[] clients={clientSocketAddress1, clientSocketAddress2, clientSocketAddress3};

		InetSocketAddress routerSocketAddress1 = new InetSocketAddress("localhost",2010);
		InetSocketAddress routerSocketAddress2 = new InetSocketAddress("localhost",2011);
		InetSocketAddress[] routers={routerSocketAddress1, routerSocketAddress2};

		InetSocketAddress trader = new InetSocketAddress("localhost",2020);

		LiveMarketData liveMarketData = new SampleLiveMarketData();

		/** change this to a runnable */
		(new MockOM("Order Manager",routers,clients,trader,liveMarketData)).start();
	}
}