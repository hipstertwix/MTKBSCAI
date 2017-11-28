import java.util.ArrayList;
import java.util.EventListener;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;

public class TestBot1 extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
    
    private boolean needForPylon = false;
    private boolean buildingPylon = false;
    
    private int reservedMinerals = 0;
    private int reservedGas = 0;
    
    private ArrayList<Unit> busyProbes;
    
    public void run() {
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit created: " + unit.getType());
        
        if(unit.getType() == UnitType.Protoss_Pylon) {
        	buildingPylon = false;
        	reservedMinerals -= 100;
        	game.sendText("buildingPylon = false! Reserved Minerals: "+ reservedMinerals);
        }
    }
    

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();
        
        game.sendText("black sheep wall");
        game.sendText("operation cwal");

        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        busyProbes = new ArrayList<>();
        
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
        //game.setTextSize(10);
        game.drawTextScreen(10, 10, "Playing as " + self.getName() + " - " + self.getRace());
        
        //if shortage of supplies, build a pylon.
        if(self.supplyUsed() >= self.supplyTotal() && !needForPylon && !isWarping(UnitType.Protoss_Pylon)) {
        	System.out.println("We need a Pylon");
        	needForPylon = true;
        	reservedMinerals += 100;
        } else if(self.supplyUsed() < self.supplyTotal()) {
        	needForPylon = false;
        }
        
        //iterate through my units
        for (Unit myUnit : self.getUnits()) {
        	
        	if(!myUnit.exists()) {
        		continue;
        	}
        	
            //if there's enough minerals, train an SCV
            if (myUnit.getType() == UnitType.Protoss_Nexus && canAfford(UnitType.Protoss_Probe)) {
                myUnit.train(UnitType.Protoss_Probe);
            }

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker()) {
            	if(myUnit.isIdle()) {
            		busyProbes.remove(myUnit);
                	gatherMineral(myUnit);
                }
                
            	if(needForPylon && canAfford(UnitType.Protoss_Pylon) && !buildingPylon && !isWarping(UnitType.Protoss_Pylon)) {
            		
            		TilePosition tp = getBuildPosition(UnitType.Protoss_Pylon, myUnit);
            		
            		if(tp != null) {
            			buildingPylon = true;
            			myUnit.build(UnitType.Protoss_Pylon, tp);
            			busyProbes.add(myUnit);
            			System.out.println("Building Pylon = true");
            		}
            	}
            }
        }
    }
    
	private boolean isWarping(UnitType type) {
		for(Unit unit : self.getUnits()) {
			if(unit.exists() && unit.getType() == type && !unit.isCompleted()) {
				return true;
			}
		}
		return false;
	}

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

	private boolean canAfford(UnitType type) {
		return type.mineralPrice() <= self.minerals() - reservedMinerals 
				&& type.gasPrice() <= self.gas() - reservedGas;
	}

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