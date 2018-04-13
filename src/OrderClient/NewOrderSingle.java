package OrderClient;

import java.io.Serializable;

import Ref.Instrument;

public class NewOrderSingle implements Serializable{
	public int size;
	public float price;
	public Instrument instrument;
	public char OrdStatus;

	public NewOrderSingle(int size,float price,Instrument instrument){
		this.size=size;
		this.price=price;
		this.instrument=instrument;
		this.OrdStatus='A';
	}
}