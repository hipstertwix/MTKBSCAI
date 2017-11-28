import bwapi.*;

public class BuildOrderBuilding implements BuildOrder {

	private UnitType uType;
	private Unit probe;
	private TilePosition tp;
	
	private Unit warpingUnit;
	
	
	public BuildOrderBuilding(UnitType uType, Unit probe, TilePosition tp) {
		this.probe = probe;
		this.tp = tp;
		this.uType = uType;
		
		probe.build(uType, tp);
	}
	
	@Override
	public void onFrame() {
		
		if(probe != null && !probe.exists()) {
			probe = null;
		}
		
		if(probe != null && !probe.isConstructing()) {
			probe.build(uType, tp);
		}
	}
	
	public boolean isDone() {
		if(warpingUnit == null) {
			return false;
		} else {
			return warpingUnit.isCompleted();
		}
	}

	@Override
	public Unit getProbe() {
		return probe;
	}
	
	public boolean checkValidity(Unit building) {
		if(warpingUnit != null) {
			return false;
		} else {
			return building.getType() == uType 
				&& building.getTilePosition() == tp;
		}
	}
	
	public void startedWarp(Unit building) {
		this.warpingUnit = building;
		this.probe = null;
	}
	
	public String toString() {
		return uType.toString() +"    " +tp.toString();
	}
	
	public UnitType getType() {
		return uType;
	}

}
