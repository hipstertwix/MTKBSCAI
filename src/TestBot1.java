import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {
	
	private static final int NEXUS_RESOURCE_RADIUS = 320;

	private final boolean CHEAT = false;

    private Mirror mirror = new Mirror();
    private Game game;
    private Player self;
    private Random random;
    
    private ArrayList<UnitType> buildQueue;
    private ArrayList<BuildingOrder> buildingOrders;
    
    private int reservedMinerals = 0;
    private int reservedGas = 0;
    
    private HashMap<Unit, ArrayList<Unit>> nexusProbes;
    private HashMap<Unit, Integer> probeNexusWaitTime;
    
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        
        if(unit.getType().isBuilding() && unit.getPlayer() == self) {
        	System.out.println("we got a new building: " + unit.toString() + "@" + unit.getTilePosition().getX() + "," + unit.getTilePosition().getY());
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
        	
        	if(unit.getType() == UnitType.Protoss_Nexus) {
        		nexusProbes.put(unit, new ArrayList<Unit>());
        	}
        }
    }
    

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();
        random = new Random();
        
        reservedMinerals = 0;
        reservedGas = 0;
        
        nexusProbes = new HashMap<>();
        probeNexusWaitTime = new HashMap<>();

        if(CHEAT) {
        	game.enableFlag(1); // enables player input
	        game.sendText("black sheep wall"); // enables full vision
	        game.sendText("operation cwal"); // increases build/train speed 
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
        
        //draw the onScreen text displays
        onFrameDraw();
        
        
        int potentialSupply = getPotentialSupply();

        //if shortage of supplies, build a pylon.
        if(self.supplyUsed() >= self.supplyTotal() + potentialSupply && !isWarping(UnitType.Protoss_Pylon)) {
        	System.out.println("We need a Pylon");
        	buildQueue.add(0, UnitType.Protoss_Pylon);
        	reservedMinerals += 100;
        }
        
        //iterate through my units
        for (Unit unit : self.getUnits()) {
        	
        	if(!unit.exists()) {
        		continue;
        	}
        	
        	if(!unit.isCompleted()) {
        		continue;
        	}
        	
        	switch(unit.getType().toString()) { 
        	case "Protoss_Nexus":
        		break;
        	case "Protoss_Probe":
                //if it's a worker and it's idle, send it to the closest mineral patch
                onFrameProbe(unit);
        		break;
        	default: continue;
        	}
        	
            //if there's enough minerals, train a probe
            if (unit.getType() == UnitType.Protoss_Nexus && canAfford(UnitType.Protoss_Probe)) {
                unit.train(UnitType.Protoss_Probe);
            }
        }
    }


	private void onFrameProbe(Unit probe) {
		if(probe.getType() != UnitType.Protoss_Probe) {
			return;
		}
		
		// assign to nexus if needed
		if(probe.isIdle() && getNexusFromProbe(probe) == null) {
			if(probeNexusWaitTime.getOrDefault(probe, 1) <= 0) {
				probeNexusWaitTime.remove(probe);
			}
			
			if(probeNexusWaitTime.getOrDefault(probe, null) == null) {
				
				if(!findNexus(probe)) {
					System.out.println("could not find nexus for probe");
					probeNexusWaitTime.put(probe, 100);
				}
			} else {
				probeNexusWaitTime.put(probe, probeNexusWaitTime.get(probe)-1);
			}
		} 
		
		if(probe.isIdle()) {
			Unit nexus = getNexusFromProbe(probe);
			if(nexus != null) {
				// need to make probe work for nexus
				List<Unit> units = nexus.getUnitsInRadius(NEXUS_RESOURCE_RADIUS);
				List<Unit> minerals = new ArrayList<>();
				for(Unit unit : units) {
					if(unit.getType().isMineralField()) {
						minerals.add(unit);
					}
				}
				probe.gather(minerals.get(random.nextInt(minerals.size())));
			}
		}
		
		if(!buildQueue.isEmpty() && buildQueue.get(0).isBuilding() && canAfford(buildQueue.get(0))) {
			UnitType uType = buildQueue.remove(0);

			TilePosition tp = getBuildPosition(uType, probe);

			if(tp != null) {
				BuildingOrder buildOrder= new BuildingOrder(uType, probe, tp);

				buildingOrders.add(buildOrder);

				System.out.println("Building a "+ uType.toString());
			}
		}
	}

	private boolean findNexus(Unit probe) {
		if(probe.getType() != UnitType.Protoss_Probe) {
			throw new IllegalArgumentException("Unit "+ probe.getType()+" is not a probe.");
		}
		
		ArrayList<Unit> nexusList = new ArrayList<>();
		
		for(Unit u : nexusProbes.keySet()) {
			if(nexusList.isEmpty()) {
				nexusList.add(u);
			} else {
				for(int i = 0; i < nexusList.size(); i++) {
					if(u.getDistance(probe) <= nexusList.get(i).getDistance(probe)) {
						nexusList.add(i, u);
						break;
					}
					
					if(i == nexusList.size()-1) {
						nexusList.add(u);
					}
				}
			}
		}
		
		for(Unit nexus : nexusList) {
			if(nexusProbes.get(nexus).size() < getNexusDesiredProbes(nexus)) {
				nexusProbes.get(nexus).add(probe);
				return true;
			}
		}
		return false;
	}
	
	private int getNexusDesiredProbes(Unit nexus) {;
		int desiredProbes = 0;		
			
		for(Unit unit : nexus.getUnitsInRadius(NEXUS_RESOURCE_RADIUS)) {
			UnitType type = unit.getType();
			if(type.isMineralField() || type == UnitType.Protoss_Assimilator) {
				desiredProbes += 3;
			}
		}
		return desiredProbes;
	}


	/**
	 * Gets the Nexus the given Probe is assigned to, or null if it does not have a Nexus assigned
	 * @param probe
	 * @return
	 */
	private Unit getNexusFromProbe(Unit probe) {
		if(probe.getType() != UnitType.Protoss_Probe) {
			throw new IllegalArgumentException("Unit "+ probe.getType()+" is not a probe.");
		}
		for(Unit nexus : nexusProbes.keySet()) {
			if(nexusProbes.get(nexus).contains(probe)) {
				return nexus;
			}
		}
		return null;
	}


	private void onFrameDraw() {
		StringBuilder bO = new StringBuilder("BuildOrders: \n");
        for(BuildingOrder b : buildingOrders) {
        	bO.append(b.toString()+"\n");
        }
        
        StringBuilder bQ = new StringBuilder("BuildQueue: \n");
        for(UnitType uType : buildQueue) {
        	bQ.append(uType.toString()+"\n");
        }
        
        StringBuilder rMaG = new StringBuilder("Reserved Minerals: "+reservedMinerals+"\n"
        		+ "Reserved Gas: "+reservedGas);   
        
        game.drawTextScreen(150, 10, bO.toString());
        game.drawTextScreen(10, 10,bQ.toString());
        game.drawTextScreen(290, 10,rMaG.toString());
        
        for(Unit nexus : nexusProbes.keySet()) {
        	int probes = nexusProbes.get(nexus).size();
        	int want = getNexusDesiredProbes(nexus);
        	String nexusString = String.format("%d/%d", probes, want);
        	game.drawText(bwapi.CoordinateType.Enum.Map, nexus.getX(), nexus.getY(), nexusString);
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