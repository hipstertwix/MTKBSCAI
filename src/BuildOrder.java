import bwapi.*;

public interface BuildOrder {
	
	public void onFrame();
	
	public boolean isDone();
	
	public Unit getProbe();
	
	public UnitType getType();
}
