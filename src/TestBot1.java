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
    
    private TilePosition tpGlobal;

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
        	System.out.println("Pylon build! Reserved Minerals: "+ reservedMinerals);
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
        
        if(tpGlobal != null) {
        	game.drawDotMap(new Position(this.tpGlobal.getX() * 32, this.tpGlobal.getY() * 32),Color.Red);
        }
        
        //if shortage of supplies, build a pylon.
        if(self.supplyUsed() >= self.supplyTotal() && !needForPylon && !isWarping(UnitType.Protoss_Pylon)) {
        	System.out.println("Planning on building a Pylon..");
        	needForPylon = true;
        }
        
        //iterate through my units
        for (Unit myUnit : self.getUnits()) {

            //if there's enough minerals, train an SCV
            if (myUnit.getType() == UnitType.Protoss_Nexus && canAfford(UnitType.Protoss_Probe)) {
                myUnit.train(UnitType.Protoss_Probe);
            }

            //if it's a worker and it's idle, send it to the closest mineral patch
            if (myUnit.getType().isWorker()) {
                if(myUnit.isIdle()) {
                	gatherMineral(myUnit);
                }
                
            	if(needForPylon && canAfford(UnitType.Protoss_Pylon) && !buildingPylon) {
            		buildingPylon = true;
            		reservedMinerals += 100;
            		
            		TilePosition tp = getBuildPosition(UnitType.Protoss_Pylon, myUnit);
            		
            		if(tp == null) {
            			buildingPylon = false;
            			reservedMinerals -= 100;
            		} else {
            			myUnit.build(UnitType.Protoss_Pylon, tp);
            			game.sendText("Building Pylon");
            			tpGlobal = tp;
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
		for(int i = 1; i < 10; i++) {
			for(int j = -i; j < i; j++) {
				for(int k = -i; k < i; k++) {
				
					if(canBuild(building, probe, j, k)) {
						return new TilePosition(probe.getTilePosition().getX()+j, probe.getTilePosition().getY()+k);
					}
				}
			}
		}
		return null;
	}

	private boolean canBuild(UnitType building, Unit probe, int j, int k) {
		TilePosition tp = new TilePosition(probe.getTilePosition().getX()+j,probe.getTilePosition().getY()+k);
		
		if(game.canBuildHere(tp, building, probe)) {
			for(Unit unit : game.getAllUnits()) {
				
				for(int width = 0; width < building.width(); width++) {
					for(int height = 0; height < building.height(); height++) {
						
						TilePosition newTP = new TilePosition(tp.getX()+width, tp.getY()+height);
						
						if(unit.getTilePosition().equals(newTP)) {
							return false;
						}
					}
				}
			}
			return true;
		}
		return false;
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