/*
 *  This file is part of Shadowlands RoadTrip - A vehicle logbook for Android.
 *
 *  This file Copyright (C) 2010,2012,2014-2015 Jeremy D Monin <jdmonin@nand.net>
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.shadowlands.roadtrip.db;

/**
 * Parent for specific object types backed by db records.
 *<P>
 * If your records will appear in output to the user (GUI or text),
 * don't forget to override <tt>toString()</tt>.
 *<P>
 * For queries, most subclasses support one or more
 * static methods which return several objects, or <tt>null</tt> if none match.
 * An example might be: <BR> 
 * <tt>static Vector&lt;ClassName> getAll({@link RDBAdapter}, <em>criteria</em>)</tt>
 * <BR>
 *  or: <BR>
 * <tt>static Vector&lt;TStop> stopsForTrip(RDBAdapter db, Trip trip)</tt>
 *<P>
 * <B>I18N:</B> Some classes' objects or methods have static text for UI elements,
 * such as the "Other..." placeholder {@link Vehicle#OTHER_VEHICLE}.  To localize all
 * static text, call {@link #localizeStatics(String)} during app startup.
 *
 * @author JM 
 */
public abstract class RDBRecord
{

	/**
	 * Localize any UI text, such as {@link Vehicle#OTHER_VEHICLE},
	 * contained in placeholder objects or returned from methods.
	 * @param other Localized text for "Other..."
	 * @since 0.9.50
	 */
	public static final void localizeStatics(final String other)
	{
		Vehicle.OTHER_VEHICLE.setModel(other);
	}

	/**
	 * connection to database.
	 * Null for new records, must never become null once read from or written to the db.
	 */
	protected RDBAdapter dbConn;

	/** primary key field "_id" */
	protected int id;

	/** does it have field changes that aren't yet committed? */
	protected boolean dirty;

	/**
	 * For use when creating a new record.
	 * id is -1, dirty is true.
	 * When ready to commit this, call {@link #insert(RDBAdapter)}.
	 * Or, if it's already in the database,
	 * call {@link #isCleanFromDB(RDBAdapter, int)} to set id
	 * and clear the dirty flag.
	 */
	protected RDBRecord()
	{
		dbConn = null;
		id = -1;
		dirty = true;
	}

	/**
	 * for use with existing record. Not dirty, id known.
	 * Subclasses should know the ID when calling this super constructor.
	 * @param db db connection
	 * @param id primary key, or -1 if not yet known when super is called;
	 *    please set it before returning from your constructor.
	 * @throws IllegalArgumentException if db is null
	 * @throws RDBKeyNotFoundException if the record's primary key or id isn't found.
	 *    This super constructor doesn't check the ID against the database;
	 *    it's declared here so that subclasses' public db-based constructors
	 *    will consistently declare it.
	 */
	protected RDBRecord(RDBAdapter db, final int id)
	    throws IllegalArgumentException, RDBKeyNotFoundException
	{
		if (db == null)
			throw new IllegalArgumentException("db null");
		dbConn = db;
		this.id = id;
		dirty = false;
	}

	/**
	 * Get this record's primary key.
	 * @return primary key field "_id", or -1 if record is new.
	 */
	public int getID() { return id; }

	/**
	 * Are this record's contents changed from the database?
	 * If true, call {@link #commit()} when ready to send to the database.
	 * Newly created RDBRecord objects are dirty; call {@link #insert(RDBAdapter)}
	 * to commit them for the first time.
	 *
	 * @return true if dirty
	 */
	public boolean isDirty() { return dirty; }

	/**
	 * This record came from the database; set its id and connection,
	 * and clear its dirty flag.
	 * For use after the default 0-argument constructor.
	 * @param db db connection
	 * @param id primary key, or -1 if not yet known; please set it before returning from your constructor.
	 * @throws IllegalStateException if old id wasn't -1
	 * @throws IllegalArgumentException if new <tt>id</tt> is -1, or <tt>db</tt> null
	 */
	protected void isCleanFromDB(RDBAdapter db, final int id)
	    throws IllegalStateException, IllegalArgumentException
	{
		if (this.id != -1)
			throw new IllegalStateException("old id was not -1");
		if ((id == -1) || (db == null))
			throw new IllegalArgumentException("bad new id or db conn");
		this.id = id;
		dbConn = db;
		dirty = false;
	}

	/**
	 * First commit of a new record.
	 * Clears dirty field; sets id and dbConn fields.
	 * @param db connection to use
	 * @return id field of newly created record
     * @throws IllegalStateException if the insert fails
	 */
	public abstract int insert(RDBAdapter db)
        throws IllegalStateException;

	/**
	 * Commit changes to an existing record.
	 * Commits to the database; clears dirty field.
	 *<P>
	 * For new records, <b>do not call commit</b>:
	 * use {@link #insert(RDBAdapter)} instead.
     * @throws IllegalStateException if the update fails
     * @throws NullPointerException if dbConn was null because
     *     this is a new record, not an existing one
	 */
	public abstract void commit()
        throws IllegalStateException, NullPointerException;

	/**
	 * Delete an existing record.
	 * After your subclass deletes it at the database level, please call
	 * {@link #deleteCleanup()} to clear common fields.
	 *
     * @throws NullPointerException if dbConn was null because
     *     this is a new record, not an existing one
     * @throws UnsupportedOperationException if this table doesn't
     *     allow deletion. Some tables never allow it, some tables
     *     only allow deletion if the row isn't referenced by other tables.
     *     If this exception may be thrown, this must be explained
     *     in the javadoc for that table object's delete method.
	 */
	public abstract void delete()
	    throws NullPointerException, UnsupportedOperationException;

	/**
	 * Convenience method for your {@link #delete()} to call.
	 * - clear dbConn
	 * - clear id (set id to -1)
	 * - set dirty flag
	 */
	protected void deleteCleanup()
	{
		
	}

	/**
	 * For data integrity, validate that db.{@link RDBAdapter#hasSameOwner(RDBAdapter) hasSameOwner}(rec.dbConn).
	 * If all is OK, do nothing, otherwise throw the exception.  See {@code hasSameOwner} javadoc for details.
	 *<P>
	 * Before v0.9.40, this method was in {@link Settings}.
	 *
	 * @param db  conn to use. Can be null only if {@code rec} is new
	 * @param rec  record to validate dbConn; must not be null
	 * @throws IllegalArgumentException if {@code db} is null when {@code rec} isn't new,
	 *         or if <tt>rec.{@link RDBRecord#dbConn dbConn}</tt> has a different owner than {@code db};
	 *         if {@code rec}'s dbconn is null, this will be in the exception detail text.
	 * @since 0.9.40
	 */
	protected static void matchDBOrThrow(RDBAdapter db, RDBRecord rec)
		throws IllegalArgumentException
	{
		if (rec.dbConn == null)
		{
			if (db == null)
				return;

			throw new IllegalArgumentException("null dbConn in RDBRecord");
		}
		if (db == null)
			throw new IllegalArgumentException("null RDBAdapter");
		if (! db.hasSameOwner(rec.dbConn))
			throw new IllegalArgumentException("Wrong dbConn in RDBRecord");
	}

}  // public abstract class RDBRecord
