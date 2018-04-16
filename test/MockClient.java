import OrderClient.NewOrderSingle;

import java.io.IOException;
import java.util.Random;

class MockClient extends Thread{

    int port;

    MockClient(String name,int port){
        this.port=port;
        this.setName(name);
    }

    private String randomOrderType(){
        Random r = new Random();
        int i = r.nextInt(2);
        if (i == 0){
            return "buy";
        } else {
            return "sell";
        }
    }

    private int numberOfOrders(){
        Random r = new Random();
        int i = r.nextInt(5)+1;
        return i;
    }

    public void run(){
        try {
            SampleClient client = new SampleClient(port);
            if(port==2000){
                for(int i = 0; i <= numberOfOrders(); i++){
                    client.sendOrder(randomOrderType());
                }
                //TODO client.sendCancel(id);
                client.messageHandler();
            }else if (port==2001){
                for(int i = 0; i <= numberOfOrders(); i++){
                    client.sendOrder(randomOrderType());
                }
                client.messageHandler();
            } else {
                for(int i = 0; i <= numberOfOrders(); i++){
                    client.sendOrder(randomOrderType());
                }
                client.messageHandler();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}