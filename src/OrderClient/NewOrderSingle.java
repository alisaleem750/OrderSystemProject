package OrderClient;

import java.io.Serializable;

import Ref.Instrument;

public class NewOrderSingle implements Serializable{
	public String type;
	public int size;
	public float price;
	public Instrument instrument;

	public NewOrderSingle(String type, int size,float price,Instrument instrument){
		this.type=type;
		this.size=size;
		this.price=price;
		this.instrument=instrument;
	}

	public int getFixTagOrderType() {
		if(type.equals("buy")){
			return 1;
		} else {
			return 2;
		}
	}
}