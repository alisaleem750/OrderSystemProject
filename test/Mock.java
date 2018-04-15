
public class Mock{
	/** this should be in a display manager */
	public static void show(String out){System.err.println(Thread.currentThread().getName()+": "+out);}
}