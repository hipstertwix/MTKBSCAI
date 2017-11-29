import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {
	
	private final boolean CHEAT = false;

    private Mirror mirror = new Mirror();
    private Game game;
    private Player self;
    
    private ArrayList<UnitType> buildQueue;
    private ArrayList<BuildingOrder> buildingOrders;
    
    private int reservedMinerals = 0;
    private int reservedGas = 0;    
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit created: " + unit.getType());
        
        if(unit.getType().isBuilding() && unit.getPlayer() == self) {
        	System.out.println("we got a new building: " + unit + "@" + unit.getTilePosition().getX() + "," + unit.getTilePosition().getY());
        	reservedMinerals -= unit.getType().mineralPrice();
        	reservedGas -= unit.getType().gasPrice();
        	
        	for(BuildingOrder b : buildingOrders) {
        		if(b.checkBuildingMatch(unit)) {
        			b.startedWarp(unit);
        			break;
        		} else {
        			System.out.println("this building did not match " + b);
        		}
        	}
        }
    }
    

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();
        
        if(CHEAT) {
	        game.sendText("black sheep wall");
	        game.sendText("operation cwal");
        }

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        buildQueue = new ArrayList<>();
        buildingOrders = new ArrayList<>();
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	System.out.println();
        }
    }

    @Override
    public void onFrame() {
        
        ArrayList<BuildingOrder> toRemove = new ArrayList<>();
        
        for(BuildingOrder b : buildingOrders) {
        	if(b.isDone()) {
        		toRemove.add(b);
        	}
        }
        
        for(BuildingOrder b : toRemove) {
        	buildingOrders.remove(b);
        }
        
        for(BuildingOrder b : buildingOrders) {
        	b.onFrame();
        }
        
        StringBuilder sb = new StringBuilder("BuildOrders: \n");
        for(BuildingOrder b : buildingOrders) {
        	sb.append(b.toString()+"\n");
        }
        
        StringBuilder sb2 = new StringBuilder("BuildQueue: \n");
        for(UnitType uType : buildQueue) {
        	sb2.append(uType.toString()+"\n");
        }
        
        game.drawTextScreen(150, 10, sb.toString());
        game.drawTextScreen(10, 10,sb2.toString());
        
        
        int potentialSupply = getPotentialSupply();

        //if shortage of supplies, build a pylon.
        if(self.supplyUsed() >= self.supplyTotal() + potentialSupply && !isWarping(UnitType.Protoss_Pylon)) {
        	System.out.println("We need a Pylon");
        	buildQueue.add(0, UnitType.Protoss_Pylon);
        	reservedMinerals += 100;
        }
        
        //iterate through my units
        for (Unit myUnit : self.getUnits()) {
        	
        	if(!myUnit.exists()) {
        		continue;
        	}
        	
            //if there's enough minerals, train a probe
            if (myUnit.getType() == UnitType.Protoss_Nexus && canAfford(UnitType.Protoss_Probe)) {
                myUnit.train(UnitType.Protoss_Probe);
            }

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker()) {
            	if(myUnit.isIdle()) {
                	gatherMineral(myUnit);
                }
            	
            	if(!buildQueue.isEmpty() && buildQueue.get(0).isBuilding() && canAfford(buildQueue.get(0))) {
            		UnitType uType = buildQueue.remove(0);
            		
            		TilePosition tp = getBuildPosition(uType, myUnit);
            		
            		if(tp != null) {
            			BuildingOrder buildOrder= new BuildingOrder(uType, myUnit, tp);
            			
            			buildingOrders.add(buildOrder);
            			
            			System.out.println("Building a "+ uType.toString());
            		}
            	}
            }
        }
    }
    
    /**
     * Checks whether or not the given unit is a busy, which for now only means it is in the process of 
     * warping in a building
     * @param probe
     * @return
     */
    private boolean isBusyProbe(Unit probe) {
    	for(BuildingOrder b : buildingOrders) {
    		if(b.getProbe() == probe) {
    			return true;
    		}
    	}
    	return false;
    }
    
    /**
     * Gets all the supply provided by buildings that are either in the BuildQueue or that are in the process
     * of being built
     * @return
     */
    private int getPotentialSupply() {
    	int supply = 0;
    	
    	for(UnitType t : buildQueue) {
    		if(t == UnitType.Protoss_Pylon) {
    			supply += 8;
    		} else if(t == UnitType.Protoss_Nexus) {
    			supply += 9;
    		}
    	}
    	
    	for(BuildingOrder b : buildingOrders) {
    		if(b.getType() == UnitType.Protoss_Pylon) {
    			supply += 8;
    		} else if(b.getType() == UnitType.Protoss_Nexus) {
    			supply += 9;
    		} 
    	}
		return supply;
	}

	/**
     * Checks if you have any incomplete units of a certain type, aka a building being warped in.
     * @param type UnitType you care about
     * @return true or false
     */
	private boolean isWarping(UnitType type) {
		for(Unit unit : self.getUnits()) {
			if(unit.exists() && unit.getType() == type && !unit.isCompleted()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Finds a TilePosition where the given building can be build by the given probe. Also checks for pathing, padding, and pylon power.
	 * @param building
	 * @param probe
	 * 
	 * @return TilePosition where the (upper left corner of the) building can be built
	 */
	private TilePosition getBuildPosition(UnitType building, Unit probe) {
		for(int i = 3; i < 10; i++) {
			for(int j = -i; j < i; j++) {
				for(int k = -i; k < i; k++) {
					TilePosition tpToCheck = new TilePosition(probe.getTilePosition().getX()+j, probe.getTilePosition().getY()+k);
					
					if(canBuildAround(tpToCheck, 1, probe, building)) {
						return tpToCheck;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Checks if a building can be build at a certain TilePosition, with a certain amount of padding around it to prevent
	 * pathing issues
	 * @param tpToCheck
	 * @param range padding on all sides of the building
	 * @param probe
	 * @param building
	 * @return
	 */
	private boolean canBuildAround(TilePosition tpToCheck, int range, Unit probe, UnitType building) {
		for(int i = -range; i <= range; i++) {
			for(int j = -range; j <= range; j++) {
				if(!game.canBuildHere(new TilePosition(tpToCheck.getX()+i,tpToCheck.getY()+j), building, probe)) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Checks whether or not you can afford the given UnitType while taking reserved minerals and gas into account.
	 * Does not care about anything else.
	 * @param type
	 * @return
	 */
	private boolean canAfford(UnitType type) {
		return type.mineralPrice() <= self.minerals() - reservedMinerals 
				&& type.gasPrice() <= self.gas() - reservedGas;
	}

	/**
	 * Orders this unit to gather the closest patch of minerals, if any such patch can be found.
	 * @param myUnit
	 */
	private void gatherMineral(Unit myUnit) {
		Unit closestMineral = null;

		//find the closest mineral
		for (Unit neutralUnit : game.neutral().getUnits()) {
		    if (neutralUnit.getType().isMineralField()) {
		        if (closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
		            closestMineral = neutralUnit;
		        }
		    }
		}

		//if a mineral patch was found, send the worker to gather it
		if (closestMineral != null) {
		    myUnit.gather(closestMineral, false);
		}
	}

    public static void main(String[] args) {
        new TestBot1().run();
    }
}