/*
 * Copyright 2012 ZerothAngel <zerothangel@tyrannyofheaven.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.tyrannyofheaven.bukkit.zPermissions.dao;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tyrannyofheaven.bukkit.zPermissions.model.EntityMetadata;
import org.tyrannyofheaven.bukkit.zPermissions.model.Entry;
import org.tyrannyofheaven.bukkit.zPermissions.model.Inheritance;
import org.tyrannyofheaven.bukkit.zPermissions.model.Membership;
import org.tyrannyofheaven.bukkit.zPermissions.model.PermissionEntity;
import org.tyrannyofheaven.bukkit.zPermissions.model.PermissionRegion;
import org.tyrannyofheaven.bukkit.zPermissions.model.PermissionWorld;

/**
 * Base implementation of a fully in-memory DAO.
 * 
 * @author zerothangel
 */
public abstract class BaseMemoryPermissionDao implements PermissionDao {

    private static final Comparator<Membership> MEMBERSHIP_GROUP_PRIORITY_COMPARATOR = new Comparator<Membership>() {
        @Override
        public int compare(Membership a, Membership b) {
            int pri = a.getGroup().getPriority() - b.getGroup().getPriority();
            if (pri != 0)
                return pri;
            return a.getGroup().getName().compareTo(b.getGroup().getName());
        }
    };

    private static final Comparator<Membership> MEMBERSHIP_MEMBER_COMPARATOR = new Comparator<Membership>() {
        @Override
        public int compare(Membership a, Membership b) {
            return a.getMember().compareTo(b.getMember());
        }
    };

    private MemoryState memoryState = new MemoryState();

    protected MemoryState setMemoryState(MemoryState memoryState) {
        MemoryState old = this.memoryState;
        this.memoryState = memoryState;
        return old;
    }

    protected Map<String, PermissionRegion> getRegions() {
        return memoryState.getRegions();
    }

    protected Map<String, PermissionWorld> getWorlds() {
        return memoryState.getWorlds();
    }

    protected Map<String, PermissionEntity> getPlayers() {
        return memoryState.getPlayers();
    }

    protected Map<String, PermissionEntity> getGroups() {
        return memoryState.getGroups();
    }

    protected Map<String, Set<Membership>> getReverseMembershipMap() {
        return memoryState.getReverseMembershipMap();
    }

    protected PermissionRegion getRegion(String region, boolean create) {
        PermissionRegion permissionRegion = null;
        if (region != null) {
            region = region.toLowerCase();
            permissionRegion = getRegions().get(region);
            if (permissionRegion == null) {
                if (create) {
                    permissionRegion = new PermissionRegion();
                    permissionRegion.setName(region);
                    getRegions().put(region, permissionRegion);
                    createRegion(permissionRegion);
                }
                else {
                    throw new IllegalArgumentException("No such region");
                }
            }
        }
        return permissionRegion;
    }

    protected abstract void createRegion(PermissionRegion region);

    // For unit testing
    PermissionRegion getRegion(String region) {
        return getRegions().get(region.toLowerCase());
    }

    protected PermissionWorld getWorld(String world, boolean create) {
        PermissionWorld permissionWorld = null;
        if (world != null) {
            world = world.toLowerCase();
            permissionWorld = getWorlds().get(world);
            if (permissionWorld == null) {
                if (create) {
                    permissionWorld = new PermissionWorld();
                    permissionWorld.setName(world);
                    getWorlds().put(world, permissionWorld);
                    createWorld(permissionWorld);
                }
                else {
                    throw new IllegalArgumentException("No such world");
                }
            }
        }
        return permissionWorld;
    }

    protected abstract void createWorld(PermissionWorld world);

    // For unit testing
    PermissionWorld getWorld(String world) {
        return getWorlds().get(world.toLowerCase());
    }

    protected PermissionEntity getEntity(String name, boolean group, boolean create) {
        String lname = name.toLowerCase();
        PermissionEntity entity;
        if (group)
            entity = getGroups().get(lname);
        else
            entity = getPlayers().get(lname);
        if (entity == null && create) {
            entity = new PermissionEntity();
            entity.setName(lname);
            entity.setGroup(group);
            entity.setDisplayName(name);
            if (group)
                getGroups().put(lname, entity);
            else
                getPlayers().put(lname, entity);
            createEntity(entity);
        }
        return entity;
    }

    protected abstract void createEntity(PermissionEntity entity);

    private PermissionEntity getGroup(String name) {
        PermissionEntity group = getEntity(name, true, false);
        if (group == null)
            throw new MissingGroupException(name);
        return group;
    }

    @Override
    public Boolean getPermission(String name, boolean group, String region, String world, String permission) {
        PermissionEntity entity = getEntity(name, group, false);
        if (entity == null)
            return null;
    
        PermissionRegion permissionRegion;
        try {
            permissionRegion = getRegion(region, false);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    
        PermissionWorld permissionWorld;
        try {
            permissionWorld = getWorld(world, false);
        }
        catch (IllegalArgumentException e) {
            return null;
        }
    
        for (Entry entry : entity.getPermissions()) {
            if (entry.getPermission().equalsIgnoreCase(permission) &&
                (permissionRegion == null ? entry.getRegion() == null : permissionRegion.equals(entry.getRegion())) &&
                (permissionWorld == null ? entry.getWorld() == null : permissionWorld.equals(entry.getWorld()))) {
                return entry.isValue();
            }
        }
        return null;
    }

    @Override
    public void setPermission(String name, boolean group, String region, String world, String permission, boolean value) {
        PermissionEntity owner;
        if (group) {
            owner = getGroup(name);
        }
        else {
            owner = getEntity(name, group, true);
        }
    
        PermissionRegion permissionRegion = getRegion(region, true);
    
        PermissionWorld permissionWorld = getWorld(world, true);
    
        permission = permission.toLowerCase();
    
        Entry found = null;
        for (Entry entry : owner.getPermissions()) {
            if (permission.equals(entry.getPermission()) &&
                    (permissionRegion == null ? entry.getRegion() == null : permissionRegion.equals(entry.getRegion())) &&
                    (permissionWorld == null ? entry.getWorld() == null : permissionWorld.equals(entry.getWorld()))) {
                found = entry;
                break;
            }
        }
    
        if (found == null) {
            found = new Entry();
            found.setEntity(owner);
            found.setRegion(permissionRegion);
            found.setWorld(permissionWorld);
            found.setPermission(permission);
            
            owner.getPermissions().add(found);
        }
    
        found.setValue(value);
        createOrUpdateEntry(found);
    }

    protected abstract void createOrUpdateEntry(Entry entry);

    @Override
    public boolean unsetPermission(String name, boolean group, String region, String world, String permission) {
        PermissionEntity entity = getEntity(name, group, false);
        if (entity == null)
            return false;
    
        PermissionRegion permissionRegion;
        try {
            permissionRegion = getRegion(region, false);
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    
        PermissionWorld permissionWorld;
        try {
            permissionWorld = getWorld(world, false);
        }
        catch (IllegalArgumentException e) {
            return false;
        }

        permission = permission.toLowerCase();

        for (Iterator<Entry> i = entity.getPermissions().iterator(); i.hasNext();) {
            Entry entry = i.next();
            if (entry.getPermission().equals(permission) &&
                    (permissionRegion == null ? entry.getRegion() == null : permissionRegion.equals(entry.getRegion())) &&
                    (permissionWorld == null ? entry.getWorld() == null : permissionWorld.equals(entry.getWorld()))) {
                i.remove();
                deleteEntry(entry);
                cleanWorldsAndRegions();
                return true;
            }
        }
        return false;
    }

    protected abstract void deleteEntry(Entry entry);

    @Override
    public void addMember(String groupName, String member, Date expiration) {
        member = member.toLowerCase();
        if (expiration != null)
            expiration = new Date(expiration.getTime());

        PermissionEntity group = getGroup(groupName);
    
        Membership found = null;
        for (Membership membership : group.getMemberships()) {
            if (membership.getMember().equals(member))
                found = membership;
        }
    
        if (found == null) {
            found = new Membership();
            found.setMember(member);
            found.setGroup(group);

            group.getMemberships().add(found);
            rememberMembership(found);
        }
        found.setExpiration(expiration);

        createOrUpdateMembership(found);
    }

    protected abstract void createOrUpdateMembership(Membership membership);

    @Override
    public boolean removeMember(String groupName, String member) {
        member = member.toLowerCase();
        PermissionEntity group = getGroup(groupName);
        
        for (Iterator<Membership> i = group.getMemberships().iterator(); i.hasNext();) {
            Membership membership = i.next();
            if (membership.getMember().equals(member)) {
                i.remove();
                deleteMembership(membership);
                forgetMembership(membership);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<Membership> getGroups(String member) {
        List<Membership> result = new ArrayList<Membership>();
        Set<Membership> memberships = getReverseMembershipMap().get(member.toLowerCase());
        if (memberships != null) {
            result.addAll(memberships);
            Collections.sort(result, MEMBERSHIP_GROUP_PRIORITY_COMPARATOR);
        }
        return result;
    }

    @Override
    public List<Membership> getMembers(String group) {
        PermissionEntity groupEntity = getEntity(group, true, false);
        if (groupEntity == null)
            return new ArrayList<Membership>(); // compat with AvajePermissionDao
    
        List<Membership> result = new ArrayList<Membership>(groupEntity.getMemberships());
        Collections.sort(result, MEMBERSHIP_MEMBER_COMPARATOR);
        return result;
    }

    @Override
    public PermissionEntity getEntity(String name, boolean group) {
        if (group)
            return getGroups().get(name.toLowerCase());
        else
            return getPlayers().get(name.toLowerCase());
    }

    @Override
    public List<PermissionEntity> getEntities(boolean group) {
        if (group)
            return new ArrayList<PermissionEntity>(getGroups().values());
        else
            return new ArrayList<PermissionEntity>(getPlayers().values());
    }

    @Override
    public void setGroup(String playerName, String groupName, Date expiration) {
        playerName = playerName.toLowerCase();
        if (expiration != null)
            expiration = new Date(expiration.getTime());
        PermissionEntity group = getGroup(groupName);
    
        Membership found = null;
        Set<Membership> memberships = getReverseMembershipMap().get(playerName);
        if (memberships != null) {
            for (Membership membership : memberships) {
                if (!membership.getGroup().equals(group)) {
                    membership.getGroup().getMemberships().remove(membership);
                    deleteMembership(membership);
                }
                else {
                    found = membership;
                }
            }
        }
    
        if (found == null) {
            found = new Membership();
            found.setMember(playerName);
            found.setGroup(group);

            group.getMemberships().add(found);
        }
        found.setExpiration(expiration);

        createOrUpdateMembership(found);
        
        getReverseMembershipMap().remove(playerName);
        rememberMembership(found);
    }

    @Override
    public void setParent(String groupName, String parentName) {
        if (parentName != null)
            setParents(groupName, Collections.singletonList(parentName));
        else
            setParents(groupName, Collections.<String>emptyList());
    }

    @Override
    public void setParents(String groupName, List<String> parentNames) {
        PermissionEntity group = getGroup(groupName);
        
        Set<Inheritance> dest = new LinkedHashSet<Inheritance>(parentNames.size());
        int order = 0;
        for (String parentName : parentNames) {
            PermissionEntity parent = getGroup(parentName);

            Inheritance i = new Inheritance();
            i.setChild(group);
            i.setParent(parent);

            if (dest.contains(i))
                continue; // Don't bother with cycle check again

            i.setOrdering(order);
            order += 100;

            // Check for a cycle
            Deque<PermissionEntity> toCheck = new LinkedList<PermissionEntity>();
            toCheck.add(parent);
            while (!toCheck.isEmpty()) {
                PermissionEntity check = toCheck.removeFirst();

                if (group.equals(check)) {
                    throw new DaoException("This would result in an inheritance cycle!");
                }
                
                toCheck.addAll(check.getParents());
            }
            
            dest.add(i);
        }

        // Don't use parent field anymore
        group.setParent(null);
        setEntityParent(group, null);

        // Figure out what to add
        Set<Inheritance> toAdd = new HashSet<Inheritance>(dest);
        toAdd.removeAll(group.getInheritancesAsChild());

        // Figure out what to delete
        Set<Inheritance> toDelete = new HashSet<Inheritance>(group.getInheritancesAsChild());
        toDelete.removeAll(dest);

        // And what to update
        Set<Inheritance> toUpdate = new HashSet<Inheritance>(dest);
        toUpdate.retainAll(group.getInheritancesAsChild());

        // Add entries
        for (Inheritance i : toAdd) {
            group.getInheritancesAsChild().add(i);
            i.getParent().getInheritancesAsParent().add(i);
            createOrUpdateInheritance(i);
        }

        // Delete entries
        for (Inheritance i : toDelete) {
            group.getInheritancesAsChild().remove(i);
            i.getParent().getInheritancesAsParent().remove(i);
            deleteInheritance(i);
        }

        // Update entries (e.g. ordering)
        for (Inheritance i : toUpdate) {
            createOrUpdateInheritance(i);
        }
    }

    protected abstract void setEntityParent(PermissionEntity entity, PermissionEntity parent);

    protected abstract void createOrUpdateInheritance(Inheritance inheritance);

    protected abstract void deleteInheritance(Inheritance inheritance);

    @Override
    public void setPriority(String groupName, int priority) {
        PermissionEntity group = getGroup(groupName);
    
        group.setPriority(priority);
        
        setEntityPriority(group, priority);
    }

    protected abstract void setEntityPriority(PermissionEntity entity, int priority);

    private void cleanWorldsAndRegions() {
        // Easier to just see what is used
        Set<PermissionRegion> usedRegions = new HashSet<PermissionRegion>();
        Set<PermissionWorld> usedWorlds = new HashSet<PermissionWorld>();
        
        List<PermissionEntity> entities = new ArrayList<PermissionEntity>();
        entities.addAll(getGroups().values());
        entities.addAll(getPlayers().values());
        
        for (PermissionEntity entity : entities) {
            for (Entry entry : entity.getPermissions()) {
                if (entry.getRegion() != null)
                    usedRegions.add(entry.getRegion());
                if (entry.getWorld() != null)
                    usedWorlds.add(entry.getWorld());
            }
        }
        
        // Determine what needs to be deleted
        Set<PermissionRegion> regionsToDelete = new HashSet<PermissionRegion>(getRegions().values());
        regionsToDelete.removeAll(usedRegions);
        Set<PermissionWorld> worldsToDelete = new HashSet<PermissionWorld>(getWorlds().values());
        worldsToDelete.removeAll(usedWorlds);
        
        // Re-build lists
        getRegions().clear();
        for (PermissionRegion region : usedRegions) {
            getRegions().put(region.getName(), region);
        }
        getWorlds().clear();
        for (PermissionWorld world : usedWorlds) {
            getWorlds().put(world.getName(), world);
        }
        
        // Tell underlying DAO about deleted regions/worlds
        if (!regionsToDelete.isEmpty())
            deleteRegions(regionsToDelete);
        if (!worldsToDelete.isEmpty())
            deleteWorlds(worldsToDelete);
    }

    protected abstract void deleteRegions(Collection<PermissionRegion> regions);
    
    protected abstract void deleteWorlds(Collection<PermissionWorld> worlds);

    @Override
    public boolean deleteEntity(String name, boolean group) {
        PermissionEntity entity = getEntity(name, group, false);
        
        if (group) {
            // Deleting a group
            if (entity != null) {
                // Break parent/child relationship (in memory)
                for (Inheritance i : entity.getInheritancesAsChild()) {
                    i.getParent().getInheritancesAsParent().remove(i);
                }
                entity.getInheritancesAsChild().clear(); // meh, don't really have to
                for (Inheritance i : entity.getInheritancesAsParent()) {
                    i.getChild().getInheritancesAsChild().remove(i);
                }
                entity.getInheritancesAsParent().clear(); // meh, don't really have to
                // NB database relationships will be deleted by deleteEntity
    
                // Delete group's entity
                getGroups().remove(entity.getName());
                deleteEntity(entity);
                cleanWorldsAndRegions();
                forgetMembershipGroup(entity);
                return true;
            }
        }
        else {
            // Deleting a player
            name = name.toLowerCase();
    
            boolean found = false;
    
            // Delete memberships
            Set<Membership> memberships = getReverseMembershipMap().get(name);
            if (memberships != null) {
                for (Membership membership : memberships) {
                    membership.getGroup().getMemberships().remove(membership);
                    deleteMembership(membership);
                }
    
                getReverseMembershipMap().remove(name);

                found = true;
            }

            if (entity != null) {
                // Delete player's entity
                getPlayers().remove(entity.getName());
                deleteEntity(entity);
                cleanWorldsAndRegions();
            }
            
            return found || entity != null;
        }
        
        return false; // nothing to delete
    }

    protected abstract void deleteEntity(PermissionEntity entity);
    
    protected abstract void deleteMembership(Membership membership);

    @Override
    public List<String> getAncestry(String groupName) {
        PermissionEntity group = getEntity(groupName, true, false);
        if (group == null) // NB only time this will be null is if the default group doesn't exist
            return new ArrayList<String>();
    
        // Build list of group ancestors
        Set<String> ancestry = new LinkedHashSet<String>();
        ancestry.add(group.getDisplayName());
        Deque<PermissionEntity> toAdd = new LinkedList<PermissionEntity>(group.getParents());
        while (!toAdd.isEmpty()) {
            group = toAdd.removeFirst();
            ancestry.add(group.getDisplayName());
            
            toAdd.addAll(group.getParents());
        }
        
        // Reverse list (will be applying farthest ancestors first)
        List<String> ancestryList = new ArrayList<String>(ancestry);
        Collections.reverse(ancestryList);
    
        return ancestryList;
    }

    @Override
    public List<Entry> getEntries(String name, boolean group) {
        PermissionEntity entity = getEntity(name, group, false);
        if (entity == null) // NB special consideration for non-existent default group
            return Collections.emptyList();
    
        return new ArrayList<Entry>(entity.getPermissions());
    }

    @Override
    public boolean createGroup(String name) {
        PermissionEntity group = getEntity(name, true, false); // so we know it was created
        if (group == null) {
            group = getEntity(name, true, true);
            return true;
        }
        else
            return false;
    }

    @Override
    public List<String> getEntityNames(boolean group) {
        Collection<PermissionEntity> entities;
        if (group)
            entities = getGroups().values();
        else
            entities = getPlayers().values();
        List<String> result = new ArrayList<String>(entities.size());
        for (PermissionEntity entity : entities) {
            result.add(entity.getDisplayName());
        }
        return result;
    }

    @Override
    public Object getMetadata(String name, boolean group, String metadataName) {
        PermissionEntity entity = getEntity(name, group, false);
        if (entity == null)
            return null;

        EntityMetadata em = entity.getMetadataMap().get(metadataName.toLowerCase());
        if (em != null)
            return em.getValue();
        else
            return null;
    }

    @Override
    public List<EntityMetadata> getAllMetadata(String name, boolean group) {
        PermissionEntity entity = getEntity(name, group, false);
        if (entity == null)
            return Collections.emptyList();
        
        return new ArrayList<EntityMetadata>(entity.getMetadata());
    }

    @Override
    public void setMetadata(String name, boolean group, String metadataName, Object value) {
        PermissionEntity owner;
        if (group) {
            owner = getGroup(name);
        }
        else {
            owner = getEntity(name, group, true);
        }

        metadataName = metadataName.toLowerCase();
        
        EntityMetadata found = owner.getMetadataMap().get(metadataName);

        if (found == null) {
            found = new EntityMetadata();
            found.setEntity(owner);
            found.setName(metadataName);
            
            owner.getMetadata().add(found);
            owner.getMetadataMap().put(metadataName, found);
        }
        
        found.setValue(value);
        createOrUpdateMetadata(found);
    }

    protected abstract void createOrUpdateMetadata(EntityMetadata metadata);

    @Override
    public boolean unsetMetadata(String name, boolean group, String metadataName) {
        PermissionEntity entity = getEntity(name, group, false);
        if (entity == null)
            return false;

        metadataName = metadataName.toLowerCase();

        for (Iterator<EntityMetadata> i = entity.getMetadata().iterator(); i.hasNext();) {
            EntityMetadata em = i.next();
            if (em.getName().equals(metadataName)) {
                i.remove();
                entity.getMetadataMap().remove(metadataName);
                deleteMetadata(em);
                return true;
            }
        }
        return false;
    }

    protected abstract void deleteMetadata(EntityMetadata metadata);

    protected void rememberMembership(Membership membership) {
        Set<Membership> memberships = getReverseMembershipMap().get(membership.getMember());
        if (memberships == null) {
            memberships = new HashSet<Membership>();
            getReverseMembershipMap().put(membership.getMember(), memberships);
        }
        memberships.add(membership);
    }

    private void forgetMembership(Membership membership) {
        Set<Membership> memberships = getReverseMembershipMap().get(membership.getMember());
        if (memberships != null)
            memberships.remove(membership);
    }

    private void forgetMembershipGroup(PermissionEntity group) {
        for (Set<Membership> memberships : getReverseMembershipMap().values()) {
            for (Iterator<Membership> i = memberships.iterator(); i.hasNext();) {
                Membership membership = i.next();
                if (group.equals(membership.getGroup())) {
                    i.remove();
                    break;
                }
            }
        }
    }

    protected static PermissionEntity getEntity(MemoryState memoryState, String name, boolean group) {
        String lname = name.toLowerCase();
        PermissionEntity entity;
        if (group)
            entity = memoryState.getGroups().get(lname);
        else
            entity = memoryState.getPlayers().get(lname);
        if (entity == null) {
            entity = new PermissionEntity();
            entity.setName(lname);
            entity.setGroup(group);
            entity.setDisplayName(name);
            if (group)
                memoryState.getGroups().put(lname, entity);
            else
                memoryState.getPlayers().put(lname, entity);
        }
        return entity;
    }

    protected static PermissionRegion getRegion(MemoryState memoryState, String name) {
        name = name.toLowerCase();
        PermissionRegion region = memoryState.getRegions().get(name);
        if (region == null) {
            region = new PermissionRegion();
            region.setName(name);
            memoryState.getRegions().put(name, region);
        }
        return region;
    }

    protected static PermissionWorld getWorld(MemoryState memoryState, String name) {
        name = name.toLowerCase();
        PermissionWorld world = memoryState.getWorlds().get(name);
        if (world == null) {
            world = new PermissionWorld();
            world.setName(name);
            memoryState.getWorlds().put(name, world);
        }
        return world;
    }

    protected static void rememberMembership(MemoryState memoryState, Membership membership) {
        Set<Membership> memberships = memoryState.getReverseMembershipMap().get(membership.getMember());
        if (memberships == null) {
            memberships = new HashSet<Membership>();
            memoryState.getReverseMembershipMap().put(membership.getMember(), memberships);
        }
        memberships.add(membership);
    }

    protected static class MemoryState {
        
        private final Map<String, PermissionRegion> regions = new HashMap<String, PermissionRegion>();

        private final Map<String, PermissionWorld> worlds = new HashMap<String, PermissionWorld>();

        private final Map<String, PermissionEntity> players = new HashMap<String, PermissionEntity>();

        private final Map<String, PermissionEntity> groups = new HashMap<String, PermissionEntity>();

        private final Map<String, Set<Membership>> reverseMembershipMap = new HashMap<String, Set<Membership>>();

        public Map<String, PermissionRegion> getRegions() {
            return regions;
        }

        public Map<String, PermissionWorld> getWorlds() {
            return worlds;
        }

        public Map<String, PermissionEntity> getPlayers() {
            return players;
        }

        public Map<String, PermissionEntity> getGroups() {
            return groups;
        }

        public Map<String, Set<Membership>> getReverseMembershipMap() {
            return reverseMembershipMap;
        }

    }

}
