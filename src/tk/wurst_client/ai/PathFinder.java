/*
 * Copyright � 2014 - 2016 | Wurst-Imperium | All rights reserved.
 * 
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package tk.wurst_client.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Set;

import net.minecraft.block.*;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import tk.wurst_client.WurstClient;

public class PathFinder
{
	private final WurstClient wurst = WurstClient.INSTANCE;
	private final Minecraft mc = Minecraft.getMinecraft();
	
	private final boolean invulnerable = mc.player.capabilities.isCreativeMode;
	public final boolean creativeFlying = mc.player.capabilities.isFlying;
	public final boolean flying =
		creativeFlying || wurst.mods.flightMod.isActive();
	private final boolean immuneToFallDamage =
		invulnerable || wurst.mods.noFallMod.isActive();
	private final boolean noSlowdownActive =
		wurst.mods.noSlowdownMod.isActive();
	private final boolean jesus = wurst.mods.jesusMod.isActive();
	private final boolean spider = wurst.mods.spiderMod.isActive();
	
	private final BlockPos start;
	private final BlockPos goal;
	private PathPoint currentPoint;
	
	private HashMap<BlockPos, Float> costMap = new HashMap<>();
	private HashMap<BlockPos, BlockPos> prevPosMap = new HashMap<>();
	private PriorityQueue<PathPoint> queue =
		new PriorityQueue<>((PathPoint o1, PathPoint o2) -> {
			float d = o1.getPriority() - o2.getPriority();
			if(d > 0)
				return 1;
			else if(d < 0)
				return -1;
			else
				return 0;
		});
	
	public PathFinder(BlockPos goal)
	{
		this.start = new BlockPos(mc.player);
		this.goal = goal;
		
		costMap.put(start, 0F);
		queue.add(new PathPoint(start, getHeuristic(start)));
	}
	
	public boolean process(int limit)
	{
		for(int i = 0; i < limit && !queue.isEmpty(); i++)
		{
			// get next point
			currentPoint = queue.poll();
			
			// check if goal is reached
			// TODO: custom condition for reaching goal
			if(currentPoint.getPos().equals(goal))
				return true;
			
			// add neighbors to queue
			for(BlockPos nextPos : getNeighbors(currentPoint.getPos()))
			{
				float newTotalCost = costMap.get(currentPoint.getPos())
					+ getCost(currentPoint, nextPos);
				
				// check if there is a better way to get here
				if(costMap.containsKey(nextPos)
					&& costMap.get(nextPos) <= newTotalCost)
					continue;
				
				// get next movement direction
				BlockPos pos = currentPoint.getPos();
				Vec3i nextMove = nextPos.subtract(currentPoint.getPos());
				
				// vertical
				if(nextMove.getY() != 0)
				{
					// up: no further checks required
					
					// down: check fall damage
					if(nextMove.getY() < 0 && !canFlyAt(pos)
						&& !canFallBelow(currentPoint))
						continue;
					
					// horizontal
				}else
				{
					// check if flying, walking or jumping
					BlockPos down = pos.down();
					if(!canFlyAt(pos) && !canBeSolid(down)
						&& !down.equals(prevPosMap.get(pos)))
						continue;
				}
				
				// add this point to queue and cost map
				costMap.put(nextPos, newTotalCost);
				prevPosMap.put(nextPos, currentPoint.getPos());
				queue.add(new PathPoint(nextPos,
					newTotalCost + getHeuristic(nextPos)));
			}
		}
		return false;
	}
	
	private ArrayList<BlockPos> getNeighbors(BlockPos pos)
	{
		ArrayList<BlockPos> neighbors = new ArrayList<BlockPos>();
		
		// abort if too far away
		if(Math.abs(start.getX() - pos.getX()) > 256
			|| Math.abs(start.getZ() - pos.getZ()) > 256)
			return neighbors;
		
		// get all neighbors
		BlockPos north = pos.north();
		BlockPos east = pos.east();
		BlockPos south = pos.south();
		BlockPos west = pos.west();
		
		BlockPos northEast = north.east();
		BlockPos southEast = south.east();
		BlockPos southWest = south.west();
		BlockPos northWest = north.west();
		
		BlockPos up = pos.up();
		BlockPos down = pos.down();
		
		// flying
		boolean flying = canFlyAt(pos);
		// walking
		boolean onGround = canBeSolid(down);
		
		// player can move sideways if flying, standing on the ground, jumping
		// (one block above ground), or in a block that allows sideways movement
		// (ladder, web, etc.)
		if(flying || onGround || canBeSolid(down.down())
			|| canMoveSidewaysInMidair(pos) || canClimbUpAt(pos.down()))
		{
			// north
			boolean basicCheckNorth =
				canGoThrough(north) && canGoThrough(north.up());
			if(basicCheckNorth && (flying || canGoThrough(north.down())
				|| canSafelyStandOn(north.down())))
				neighbors.add(north);
			
			// east
			boolean basicCheckEast =
				canGoThrough(east) && canGoThrough(east.up());
			if(basicCheckEast && (flying || canGoThrough(east.down())
				|| canSafelyStandOn(east.down())))
				neighbors.add(east);
			
			// south
			boolean basicCheckSouth =
				canGoThrough(south) && canGoThrough(south.up());
			if(basicCheckSouth && (flying || canGoThrough(south.down())
				|| canSafelyStandOn(south.down())))
				neighbors.add(south);
			
			// west
			boolean basicCheckWest =
				canGoThrough(west) && canGoThrough(west.up());
			if(basicCheckWest && (flying || canGoThrough(west.down())
				|| canSafelyStandOn(west.down())))
				neighbors.add(west);
			
			// north-east
			if(basicCheckNorth && basicCheckEast && canGoThrough(northEast)
				&& canGoThrough(northEast.up())
				&& (flying || canGoThrough(northEast.down())
					|| canSafelyStandOn(northEast.down())))
				neighbors.add(northEast);
			
			// south-east
			if(basicCheckSouth && basicCheckEast && canGoThrough(southEast)
				&& canGoThrough(southEast.up())
				&& (flying || canGoThrough(southEast.down())
					|| canSafelyStandOn(southEast.down())))
				neighbors.add(southEast);
			
			// south-west
			if(basicCheckSouth && basicCheckWest && canGoThrough(southWest)
				&& canGoThrough(southWest.up())
				&& (flying || canGoThrough(southWest.down())
					|| canSafelyStandOn(southWest.down())))
				neighbors.add(southWest);
			
			// north-west
			if(basicCheckNorth && basicCheckWest && canGoThrough(northWest)
				&& canGoThrough(northWest.up())
				&& (flying || canGoThrough(northWest.down())
					|| canSafelyStandOn(northWest.down())))
				neighbors.add(northWest);
		}
		
		// up
		if(pos.getY() < 256 && canGoThrough(up.up())
			&& (flying || onGround || canClimbUpAt(pos)))
			neighbors.add(up);
		
		// down
		if(pos.getY() > 0 && canGoThrough(down))
			neighbors.add(down);
		
		return neighbors;
	}
	
	private boolean canGoThrough(BlockPos pos)
	{
		// check if loaded
		if(!mc.world.isBlockLoaded(pos, false))
			return false;
		
		// check if solid
		Material material = getMaterial(pos);
		Block block = getBlock(pos);
		if(material.blocksMovement() && !(block instanceof BlockSign))
			return false;
		
		// check if trapped
		if(block instanceof BlockTripWire
			|| block instanceof BlockPressurePlate)
			return false;
		
		// check if safe
		if(!invulnerable
			&& (material == Material.LAVA || material == Material.FIRE))
			return false;
		
		return true;
	}
	
	private boolean canFlyAt(BlockPos pos)
	{
		return flying
			|| !noSlowdownActive && getMaterial(pos) == Material.WATER;
	}
	
	private boolean canBeSolid(BlockPos pos)
	{
		Material material = getMaterial(pos);
		Block block = getBlock(pos);
		return (material.blocksMovement() && !(block instanceof BlockSign))
			|| block instanceof BlockLadder || (jesus
				&& (material == Material.WATER || material == Material.LAVA));
	}
	
	private boolean canClimbUpAt(BlockPos pos)
	{
		// check if this block works for climbing
		Block block = getBlock(pos);
		if(!spider && !(block instanceof BlockLadder)
			&& !(block instanceof BlockVine))
			return false;
		
		// check if any adjacent block is solid
		BlockPos up = pos.up();
		if(!canBeSolid(pos.north()) && !canBeSolid(pos.east())
			&& !canBeSolid(pos.south()) && !canBeSolid(pos.west())
			&& !canBeSolid(up.north()) && !canBeSolid(up.east())
			&& !canBeSolid(up.south()) && !canBeSolid(up.west()))
			return false;
		
		return true;
	}
	
	private boolean canFallBelow(PathPoint point)
	{
		// check fall damage
		if(!checkFallDamage(point))
			return false;
		
		// check if player can stand below or keep falling
		BlockPos down2 = point.getPos().down(2);
		if(!canGoThrough(down2) && !canSafelyStandOn(down2))
			return false;
		
		return true;
	}
	
	private boolean checkFallDamage(PathPoint point)
	{
		// check if fall damage is off
		if(immuneToFallDamage)
			return true;
		
		// check if fall does not end yet
		BlockPos pos = point.getPos();
		BlockPos down2 = pos.down(2);
		if(!getMaterial(down2).blocksMovement()
			|| getBlock(down2) instanceof BlockSign)
			return true;
		
		// check if fall ends with slime block
		if(getBlock(down2) instanceof BlockSlime)
			return true;
		
		// check current and previous points
		BlockPos prevPos = pos;
		for(int i = 0; i <= 3; i++)
		{
			// check if point does not exist
			if(prevPos == null)
				return true;
				
			// check if point is not part of this fall
			// (meaning the fall is too short to cause damage)
			if(!pos.up(i).equals(prevPos))
				return true;
			
			// check if block resets fall damage
			Block prevBlock = getBlock(prevPos);
			if(prevBlock instanceof BlockLiquid
				|| prevBlock instanceof BlockLadder
				|| prevBlock instanceof BlockVine
				|| prevBlock instanceof BlockWeb)
				return true;
			
			prevPos = prevPosMap.get(prevPos);
		}
		
		return false;
	}
	
	private boolean canMoveSidewaysInMidair(BlockPos pos)
	{
		// check feet
		Block blockFeet = getBlock(pos);
		if(blockFeet instanceof BlockLiquid || blockFeet instanceof BlockLadder
			|| blockFeet instanceof BlockVine || blockFeet instanceof BlockWeb)
			return true;
		
		// check head
		Block blockHead = getBlock(pos.up());
		if(blockHead instanceof BlockLiquid || blockHead instanceof BlockWeb)
			return true;
		
		return false;
	}
	
	private boolean canSafelyStandOn(BlockPos pos)
	{
		// check if solid
		Material material = getMaterial(pos);
		if(!canBeSolid(pos))
			return false;
		
		// check if safe
		if(!invulnerable
			&& (material == Material.CACTUS || material == Material.LAVA))
			return false;
		
		return true;
	}
	
	private float getCost(PathPoint lastPoint, BlockPos next)
	{
		float cost = 1F;
		
		// diagonal movement
		if(lastPoint.getPos().getX() != next.getX()
			&& lastPoint.getPos().getZ() != next.getZ())
			cost *= 1.4142135623730951F;
		
		// liquids
		Material nextMaterial = getMaterial(next);
		if(nextMaterial == Material.WATER && !noSlowdownActive)
			cost *= 1.3164437838225804F;
		else if(nextMaterial == Material.LAVA)
			cost *= 4.539515393656079F;
		
		// soul sand
		if(!canFlyAt(next) && getBlock(next.down()) instanceof BlockSoulSand)
			cost *= 2.5F;
		
		return cost;
	}
	
	private float getHeuristic(BlockPos pos)
	{
		float dx = Math.abs(pos.getX() - goal.getX());
		float dy = Math.abs(pos.getY() - goal.getY());
		float dz = Math.abs(pos.getZ() - goal.getZ());
		return 1.001F
			* ((dx + dy + dz) - 0.5857864376269049F * Math.min(dx, dz));
	}
	
	public PathPoint getCurrentPoint()
	{
		return currentPoint;
	}
	
	public Set<BlockPos> getProcessedBlocks()
	{
		return prevPosMap.keySet();
	}
	
	public PathPoint[] getQueuedPoints()
	{
		return queue.toArray(new PathPoint[queue.size()]);
	}
	
	public float getCost(BlockPos pos)
	{
		return costMap.get(pos);
	}
	
	public BlockPos getPrevPos(BlockPos pos)
	{
		return prevPosMap.get(pos);
	}
	
	public ArrayList<BlockPos> formatPath()
	{
		ArrayList<BlockPos> path = new ArrayList<BlockPos>();
		BlockPos pos = currentPoint.getPos();
		while(pos != null)
		{
			path.add(pos);
			pos = prevPosMap.get(pos);
		}
		Collections.reverse(path);
		return path;
	}
	
	private Material getMaterial(BlockPos pos)
	{
		return mc.world.getBlockState(pos).getMaterial();
	}
	
	private Block getBlock(BlockPos pos)
	{
		return mc.world.getBlockState(pos).getBlock();
	}
	
	public BlockPos getGoal()
	{
		return goal;
	}
}
