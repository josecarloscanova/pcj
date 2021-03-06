/* Copyright (C) 2017  Intel Corporation
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 only, as published by the Free Software Foundation.
 * This file has been designated as subject to the "Classpath"
 * exception as provided in the LICENSE file that accompanied
 * this code.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License version 2 for more details (a copy
 * is included in the LICENSE file that accompanied this code).
 *
 * You should have received a copy of the GNU General Public License
 * version 2 along with this program; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package lib.util.persistent;

import lib.util.persistent.types.Types;
import lib.util.persistent.types.PersistentType;
import lib.util.persistent.types.ObjectType;
import lib.util.persistent.types.ByteField;
import lib.util.persistent.types.ShortField;
import lib.util.persistent.types.IntField;
import lib.util.persistent.types.LongField;
import lib.util.persistent.types.FloatField;
import lib.util.persistent.types.DoubleField;
import lib.util.persistent.types.CharField;
import lib.util.persistent.types.BooleanField;
import lib.util.persistent.types.ObjectField;
import lib.util.persistent.types.ValueField;
import java.lang.reflect.Constructor;
import static lib.util.persistent.Trace.*;
import java.util.function.Consumer;

@SuppressWarnings("sunapi")
public class PersistentObject extends AbstractPersistentObject {
    private static final String LOCK_FAIL_MESSAGE = "failed to acquire lock (timeout) in getObject";

    public PersistentObject(ObjectType<? extends PersistentObject> type) {
        super(type);
    }

    @SuppressWarnings("unchecked")
    protected <T extends PersistentObject> PersistentObject(ObjectType<? extends PersistentObject> type, Consumer<T> initializer) {
        super(type, initializer);
    }

    <T extends AnyPersistent> PersistentObject(ObjectType<T> type, MemoryRegion region) {
        super(type, region);
        // trace(region.addr(), "created object of type %s", type.getName());
    }

    public PersistentObject(ObjectPointer<? extends AnyPersistent> p) {
        super(p);
    }

    @Override
    byte getByte(long offset) {
        // return Util.synchronizedBlock(this, () -> region().getByte(offset));
        byte ans;
        Transaction transaction = Transaction.getTransaction();
        boolean inTransaction = transaction != null && transaction.isActive();
        if (!inTransaction) {
            lock();
            ans = region().getByte(offset);
            unlock();
        }
        else {
            boolean success = tryLock(transaction);
            if (success) {
                transaction.addLockedObject(this);
                return region().getByte(offset);
            }
            else {
                if (inTransaction) throw new TransactionRetryException();
                else throw new RuntimeException("failed to acquire lock (timeout)");
            }
        }
        return ans;
    }

    @Override
    short getShort(long offset) {
        // return Util.synchronizedBlock(this, () -> region().getShort(offset));
        short ans;
        Transaction transaction = Transaction.getTransaction();
        boolean inTransaction = transaction != null && transaction.isActive();
        if (!inTransaction) {
            lock();
            ans = region().getShort(offset);
            unlock();
        }
        else {
            boolean success = tryLock(transaction);
            if (success) {
                transaction.addLockedObject(this);
                ans = region().getShort(offset);
            }
            else {
                if (inTransaction) throw new TransactionRetryException();
                else throw new RuntimeException("failed to acquire lock (timeout)");
            }
        }
        return ans;
    }

    @Override
    int getInt(long offset) {
        // return Util.synchronizedBlock(this, () -> region().getInt(offset));
        int ans;
        Transaction transaction = Transaction.getTransaction();
        boolean inTransaction = transaction != null && transaction.isActive();
        if (!inTransaction) {
            lock();
            ans = region().getInt(offset);
            unlock();
        }
        else {
            boolean success = tryLock(transaction);
            if (success) {
                transaction.addLockedObject(this);
                ans = region().getInt(offset);
            }
            else {
                if (inTransaction) throw new TransactionRetryException();
                else throw new RuntimeException("failed to acquire lock (timeout)");
            }
        }
        return ans;
    }

    @Override
    long getLong(long offset) {
        // trace(true, "PO.getLong(%d)", offset);
        // return Util.synchronizedBlock(this, () -> region().getLong(offset));
        long ans;
        Transaction transaction = Transaction.getTransaction();
        boolean inTransaction = transaction != null && transaction.isActive();
        if (!inTransaction) {
            lock();
            ans = region().getLong(offset);
            unlock();
        }
        else {
            boolean success = tryLock(transaction);
            if (success) {
                transaction.addLockedObject(this);
                ans = region().getLong(offset);
            }
            else {
                if (inTransaction) throw new TransactionRetryException();
                else throw new RuntimeException("failed to acquire lock (timeout)");
            }
        }
        return ans;
    }

    @Override
    @SuppressWarnings("unchecked")
    <T extends AnyPersistent> T getObject(long offset, PersistentType type) {
        // trace(true, "PO.getObject(%d, %s)", offset, type);
        T ans = null;
        Transaction transaction = Transaction.getTransaction();
        boolean inTransaction = transaction != null && transaction.isActive();
        if (!inTransaction) {
            if (tryLock(5000)) {
                try {
                    long objAddr = getRegionLong(offset);
                    if (objAddr != 0) ans = (T)ObjectCache.get(objAddr);
                }
                finally {unlock();}
            }
            else throw new RuntimeException(LOCK_FAIL_MESSAGE);
        }
        else {
            if (tryLock(transaction)) {
                transaction.addLockedObject(this);
                long objAddr = getRegionLong(offset);
                if (objAddr != 0) ans = (T)ObjectCache.get(objAddr);
            }
            else throw new TransactionRetryException(LOCK_FAIL_MESSAGE);
        }
        return ans;
    }

    //TODO: optimize 4 parts: acquire lock, allocate volatile, copy P->V, construct box
    @Override
    @SuppressWarnings("unchecked") 
    <T extends AnyPersistent> T getValueObject(long offset, PersistentType type) {
        // trace(true, "PO.getValueObject(%d, %s)", offset, type);
        MemoryRegion objRegion = Util.synchronizedBlock(this, () -> {
            MemoryRegion srcRegion = region();
            MemoryRegion dstRegion = new VolatileMemoryRegion(type.getSize());
            // trace(true, "getObject (valueBased) type = %s, src addr = %d, srcOffset = %d, dst  = %s, size = %d", type, srcRegion.addr(), offset, dstRegion, type.getSize());
            Util.memCopy(getType(), (ObjectType)type, srcRegion, offset, dstRegion, 0L, type.getSize());
            return dstRegion;
        });
        T obj = null;
        try {
            Constructor ctor = ((ObjectType)type).getReconstructor();
            ObjectPointer p = new ObjectPointer<T>((ObjectType)type, objRegion);
            obj = (T)ctor.newInstance(p);
        }
        catch (Exception e) {e.printStackTrace();}
        return obj;
    }

    public void setByteField(ByteField f, byte value) {setByte(offset(f.getIndex()), value);}
    public void setShortField(ShortField f, short value) {setShort(offset(f.getIndex()), value);}
    public void setIntField(IntField f, int value) {setInt(offset(f.getIndex()), value);}
    public void setLongField(LongField f, long value) {setLong(offset(f.getIndex()), value);}
    public void setFloatField(FloatField f, float value) {setInt(offset(f.getIndex()), Float.floatToIntBits(value));}
    public void setDoubleField(DoubleField f, double value) {setLong(offset(f.getIndex()), Double.doubleToLongBits(value));}
    public void setCharField(CharField f, char value) {setInt(offset(f.getIndex()), (int)value);}
    public void setBooleanField(BooleanField f, boolean value) {setByte(offset(f.getIndex()), value ? (byte)1 : (byte)0);}
    public <T extends AnyPersistent> void setObjectField(ObjectField<T> f, T value) {setObject(offset(f.getIndex()), value);}
    
    public <T extends AnyPersistent> void setObjectField(ValueField<T> f, T value) {
        // trace(true, "PO.setLongField(%s) : VF", f); 
        if (f.getType().getSize() != value.getType().getSize()) throw new IllegalArgumentException("value types do not match");
        setValueObject(offset(f.getIndex()), value);
    }
}
