package features;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import kaptainwutax.biomeutils.source.OverworldBiomeSource;
import kaptainwutax.featureutils.loot.ChestContent;
import kaptainwutax.featureutils.loot.item.ItemStack;
import kaptainwutax.featureutils.structure.RuinedPortal;
import kaptainwutax.featureutils.structure.generator.structure.RuinedPortalGenerator;
import kaptainwutax.mcutils.block.Block;
import kaptainwutax.mcutils.rand.ChunkRand;
import kaptainwutax.mcutils.state.Dimension;
import kaptainwutax.mcutils.util.data.Pair;
import kaptainwutax.mcutils.util.pos.BPos;
import kaptainwutax.mcutils.util.pos.CPos;
import kaptainwutax.mcutils.version.MCVersion;
import kaptainwutax.terrainutils.terrain.OverworldTerrainGenerator;

/**
 * Checks ruined portals for completable patterns
 * @author Pancake
 */
public class Features {

	public static void main(String[] args) throws Exception {
		// Read Cubiomes File
		FileDialog f = new FileDialog((Frame) null);
		f.setVisible(true);
		File file = f.getFiles()[0];
		List<String> lines = Files.readAllLines(file.toPath());
		// Split Cubimoes File into multiple arrays
		int arraycount = Runtime.getRuntime().availableProcessors();
		int countPerArray = lines.size() / arraycount;
		List<List<Long>> splitseeds = new ArrayList<>();
		for (int i = 0; i < arraycount; i++) {
			List<Long> seeds = new ArrayList<>();
			for (int j = 0; j < countPerArray; j++) {
				try {
					long seed = Long.parseLong(lines.get((i*countPerArray)+j));
					seeds.add(seed);
				} catch (Exception e) {}
			}
			System.out.println("Thread " + i + ": " + seeds.size() + " Seeds");
			splitseeds.add(seeds);
		}
		// Prepare new System out
		AtomicInteger threads = new AtomicInteger();
		AtomicReference<FileOutputStream> streams = new AtomicReference<>();
		File out = new File("seeds.txt");
		out.createNewFile();
		streams.set(new FileOutputStream(out));
		System.setOut(new PrintStream(System.out) {
			@Override
			public void write(byte[] b) throws IOException {
				FileOutputStream stream = streams.get();
				stream.write(b);
				if (threads.incrementAndGet() == Runtime.getRuntime().availableProcessors()) {
					System.err.println("All threads have successfully finished");
					stream.close();
				}
			}
		});
		// Search...
		System.out.println("Starting to search...");
		for (int i = 0; i < arraycount; i++) {
			final List<Long> seedsToCheck = splitseeds.get(i);
			final int threadid = i;
			new Thread(() -> {
				try {
					// Prepare Threading
					final Long[] seeds = seedsToCheck.toArray(new Long[0]);
					final ByteArrayOutputStream s = new ByteArrayOutputStream();
					// Prepare Structures
					RuinedPortal portal = new RuinedPortal(Dimension.OVERWORLD, MCVersion.v1_16_1);
					// Prepare Generators
					RuinedPortalGenerator portalgenerator = new RuinedPortalGenerator(MCVersion.v1_16_1);
					ChunkRand rand = new ChunkRand();
					for (int index = 0; index < seeds.length; index++) {
						checkSeed(seeds[index], rand, portalgenerator, portal, s);
					}
					s.close();
					System.out.println("Thread-" + threadid + " done!");
					System.out.write(s.toByteArray());
				} catch (IOException e) {
					e.printStackTrace();
				}
			}).start();
		}
	}

	/**
	 * Checks a seed and outputs to the syso if successful
	 * @param seed The World Seed
	 * @param rand Reusable Chunk Random
	 * @param portalgenerator Reuseable Portal Generator
	 * @param portal Reusable Portal
	 * @param s Output File
	 * @throws IOException File Exceptions
	 */
	private static void checkSeed(long seed, ChunkRand rand, RuinedPortalGenerator portalgenerator, RuinedPortal portal, ByteArrayOutputStream s) throws IOException {
		// Prepare Generators
		OverworldBiomeSource source = new OverworldBiomeSource(MCVersion.v1_16_1, seed);
		// Locate Ruined Portal near 190,-64 and 210,0
		CPos portalposition = portal.getInRegion(seed, 0, -1, rand);
		if (portalposition == null) 
			return;
		OverworldTerrainGenerator terrain = new OverworldTerrainGenerator(source);
		if (!portalgenerator.generate(terrain, portalposition))
			return;
		String type = portalgenerator.getType();
		if (type.startsWith("giant"))
			return;
		int typeint = type.charAt(type.length()-1)-'0';
		
		// Check if the ruined portal is finishable
		int obsidian = 0;
		boolean is_flamable = false;
		boolean is_sword = false;
		boolean is_pickaxe = false;
		boolean is_axe = false;
		for (Pair<Block, BPos> pair : portalgenerator.getMinimalPortal())
			if ("crying_obsidian".equals(pair.getFirst().getName()))
				return;
			else if ("obsidian".equals(pair.getFirst().getName()))
				obsidian+=1;
		// Check Loot for finishable ruined portal 
		for (ChestContent c : portal.getLoot(seed, portalgenerator, rand, false))
			for (ItemStack i : c.getItems())
				if ("obsidian".equals(i.getItem().getName()))
					obsidian++;
				else if ("flint_and_steel".equals(i.getItem().getName()) || "fire_charge".equals(i.getItem().getName()))
					is_flamable = true;
				else if ("golden_sword".equals(i.getItem().getName()))
					is_sword = true;
				else if ("golden_pickaxe".equals(i.getItem().getName()))
					is_pickaxe = true;
				else if ("golden_axe".equals(i.getItem().getName()))
					is_axe = true;
		if (!is_flamable)
			return;
		if (!is_sword)
			return;
		if (!is_pickaxe)
			return;
		if (!is_axe)
			return;
		// Finally check
		if (typeint == 6 && obsidian >= 12)
			s.write(("Seed found: " + seed + "\n").getBytes(StandardCharsets.UTF_8));
	}
	
}
