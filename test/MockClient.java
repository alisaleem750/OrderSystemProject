import java.io.IOException;

class MockClient extends Thread{

    int port;

    MockClient(String name,int port){
        this.port=port;
        this.setName(name);
    }

    public void run(){
        try {
            SampleClient client = new SampleClient(port);
            if(port==2000){
                client.sendOrder();
                int id=client.sendOrder();
                //TODO client.sendCancel(id);
                client.messageHandler();
            }else{
                client.sendOrder();
                client.messageHandler();
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }
}