import bwapi.*;

public class BuildingOrder {

	private UnitType toBuild;
	private Unit probe;
	private TilePosition where;
	
	private Unit warpingUnit;
	
	
	public BuildingOrder(UnitType uType, Unit probe, TilePosition tp) {
		this.probe = probe;
		this.where = tp;
		this.toBuild = uType;
		
		probe.build(uType, tp);
	}
	
	public void onFrame() {
		
		if(probe != null && !probe.exists()) {
			probe = null;
		}
		
		if(probe != null && !probe.isConstructing()) {
			probe.build(toBuild, where);
		}
	}
	
	public boolean isDone() {
		if(warpingUnit == null) {
			return false;
		} else {
			return warpingUnit.isCompleted();
		}
	}

	public Unit getProbe() {
		return probe;
	}
	
	/**
	 * Checks whether or not the given building is what this BuildingOrder is supposed to be making
	 * @param building
	 * @return
	 */
	public boolean checkBuildingMatch(Unit building) {
		if(warpingUnit != null) {
			return false;
		} else {
			boolean typeMatch = building.getType() == toBuild;
			boolean locationMatch = building.getTilePosition().getX() == where.getX()
					&& building.getTilePosition().getY() == where.getY();
			return typeMatch && locationMatch;
		}
	}
	
	/**
	 * lets this Order know that the given building is what the order is warping in. will check if it is the 
	 * right building
	 * @param building
	 */
	public void startedWarp(Unit building) {
		if(!checkBuildingMatch(building)) {
			throw new IllegalArgumentException("this is not the right building!");
		}
		this.warpingUnit = building;
		this.probe = null;
	}
	
	public String toString() {
		String str = toBuild.toString() +" " +where.toString();
		if(null != warpingUnit) {
			int progress = 100 - (100 * warpingUnit.getRemainingBuildTime()) / warpingUnit.getType().buildTime();
			str += " " + progress + "%%";
		}
		return str;
	}
	
	public UnitType getType() {
		return toBuild;
	}

}
