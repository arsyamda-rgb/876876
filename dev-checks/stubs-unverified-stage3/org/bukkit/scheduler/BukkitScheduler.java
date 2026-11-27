package org.bukkit.scheduler;
public interface BukkitScheduler { void runTask(org.bukkit.plugin.Plugin p, Runnable r); void runTaskLater(org.bukkit.plugin.Plugin p, Runnable r, long delay); Object runTaskTimer(org.bukkit.plugin.Plugin p, Runnable r, long d, long period); }
