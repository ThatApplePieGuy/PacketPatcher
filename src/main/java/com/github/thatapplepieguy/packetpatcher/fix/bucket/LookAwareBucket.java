package com.github.thatapplepieguy.packetpatcher.fix.bucket;

import net.minecraft.server.v1_8_R3.*;

public class LookAwareBucket extends ItemBucket {

    private final BucketLookStore lookStore;

    public LookAwareBucket(Block block, BucketLookStore lookStore) {
        super(block);
        this.lookStore = lookStore;
    }

    @Override
    protected MovingObjectPosition a(World world, EntityHuman entityhuman, boolean useLiquids) {
        float pitch = entityhuman.pitch;
        float yaw = entityhuman.yaw;

        if (entityhuman instanceof EntityPlayer player) {
            BucketLookStore.Look look = lookStore.consume(player.getUniqueID());
            if (look != null) {
                pitch = look.pitch();
                yaw = look.yaw();
            }
        }

        float f = pitch;
        float f1 = yaw;
        double d0 = entityhuman.locX;
        double d1 = entityhuman.locY + (double) entityhuman.getHeadHeight();
        double d2 = entityhuman.locZ;
        Vec3D vec3d = new Vec3D(d0, d1, d2);
        float f2 = MathHelper.cos(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
        float f3 = MathHelper.sin(-f1 * ((float) Math.PI / 180F) - (float) Math.PI);
        float f4 = -MathHelper.cos(-f * ((float) Math.PI / 180F));
        float f5 = MathHelper.sin(-f * ((float) Math.PI / 180F));
        float f6 = f3 * f4;
        float f7 = f2 * f4;
        double d3 = 5.0D;
        Vec3D vec3d1 = vec3d.add((double) f6 * d3, (double) f5 * d3, (double) f7 * d3);

        return world.rayTrace(vec3d, vec3d1, useLiquids, !useLiquids, false);
    }
}
