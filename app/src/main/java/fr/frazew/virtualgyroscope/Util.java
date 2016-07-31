package fr.frazew.virtualgyroscope;

public class Util {

    public static boolean checkSensorResolution(float[] prevValues, float[] values, float resolution) {
        if (Math.abs(prevValues[0] - values[0]) > resolution) return true;
        else if (Math.abs(prevValues[1] - values[1]) > resolution) return true;
        else if (Math.abs(prevValues[2] - values[2]) > resolution) return true;
        return false;
    }

    public static float[] normalizeQuaternion(float[] quaternion) {
        float[] returnQuat = new float[4];
        float sqrt = (float)Math.sqrt(quaternion[0]*quaternion[0] + quaternion[1]*quaternion[1] + quaternion[2]*quaternion[2] + quaternion[3]*quaternion[3]);

        returnQuat[0] = quaternion[0] / sqrt;
        returnQuat[1] = quaternion[1] / sqrt;
        returnQuat[2] = quaternion[2] / sqrt;
        returnQuat[3] = quaternion[3] / sqrt;

        return returnQuat;
    }

    public static float[] normalizeVector(float[] vector) {
        float[] newVec = new float[3];
        float sqrt = (float)Math.sqrt(vector[0]*vector[0] + vector[1]*vector[1] + vector[2]*vector[2]);

        newVec[0] = vector[0] / sqrt;
        newVec[1] = vector[1] / sqrt;
        newVec[2] = vector[2] / sqrt;

        return newVec;
    }

    /*
        Subtracts a quaternion by another, this a very easy operation so nothing interesting
     */
    public static float[] subtractQuaternionbyQuaternion(float[] quat1, float[] quat2) {
        float[] quaternion = new float[4];

        quaternion[0] = quat1[0] - quat2[0];
        quaternion[1] = quat1[1] - quat2[1];
        quaternion[2] = quat1[2] - quat2[2];
        quaternion[3] = quat1[3] - quat2[3];

        return quaternion;
    }

    /*
        This uses the Hamilton product to multiply the vector converted to a quaternion with the rotation quaternion.
        Returns a new quaternion which is the rotated vector.
        Source:  https://en.wikipedia.org/wiki/Quaternion#Hamilton_product
        -- Not used, but keeping it just in case
     */
    public static float[] rotateVectorByQuaternion(float[] vector, float[] quaternion) {
        float a = vector[0];
        float b = vector[1];
        float c = vector[2];
        float d = vector[3];

        float A = quaternion[0];
        float B = quaternion[1];
        float C = quaternion[2];
        float D = quaternion[3];

        float newQuaternionReal = a*A - b*B - c*C - d*D;
        float newQuaternioni = a*B + b*A + c*D - d*C;
        float newQuaternionj = a*C - b*D + c*A + d*B;
        float newQuaternionk = a*D + b*C - c*B + d*A;

        return new float[] {newQuaternionReal, newQuaternioni, newQuaternionj, newQuaternionk};
    }

    public static float[] quaternionToRotationMatrix(float[] quaternion) {
        float[] rotationMatrix = new float[9];

        float w = quaternion[0];
        float x = quaternion[1];
        float y = quaternion[2];
        float z = quaternion[3];

        float n = w * w + x * x + y * y + z * z;
        float s = n == 0 ? 0 : 2/n;
        float wx = s * w * x, wy = s * w * y, wz = s * w * z;
        float xx = s * x * x, xy = s * x * y, xz = s * x * z;
        float yy = s * y * y, yz = s * y * z, zz = s * z * z;

        rotationMatrix[0] = 1 - (yy + zz);
        rotationMatrix[1] = xy - wz;
        rotationMatrix[2] = xz + wy;
        rotationMatrix[3] = xy + wz;
        rotationMatrix[4] = 1 - (xx + zz);
        rotationMatrix[5] = yz - wx;
        rotationMatrix[6] = xz - wy;
        rotationMatrix[7] = yz + wx;
        rotationMatrix[8] = 1 - (xx + yy);

        return rotationMatrix;
    }

    /*
        Credit for this code goes to http://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToQuaternion/
        Additional credit goes to https://en.wikipedia.org/wiki/Quaternion for helping me understand how quaternions work
     */
    public static float[] rotationMatrixToQuaternion(float[] rotationMatrix) {
        float m00 = rotationMatrix[0];
        float m01 = rotationMatrix[1];
        float m02 = rotationMatrix[2];
        float m10 = rotationMatrix[3];
        float m11 = rotationMatrix[4];
        float m12 = rotationMatrix[5];
        float m20 = rotationMatrix[6];
        float m21 = rotationMatrix[7];
        float m22 = rotationMatrix[8];

        float tr = m00 + m11 + m22;

        float qw;
        float qx;
        float qy;
        float qz;
        if (tr > 0) {
            float S = (float)Math.sqrt(tr+1.0) * 2;
            qw = 0.25F * S;
            qx = (m21 - m12) / S;
            qy = (m02 - m20) / S;
            qz = (m10 - m01) / S;
        } else if ((m00 > m11)&(m00 > m22)) {
            float S = (float)Math.sqrt(1.0 + m00 - m11 - m22) * 2;
            qw = (m21 - m12) / S;
            qx = 0.25F * S;
            qy = (m01 + m10) / S;
            qz = (m02 + m20) / S;
        } else if (m11 > m22) {
            float S = (float)Math.sqrt(1.0 + m11 - m00 - m22) * 2;
            qw = (m02 - m20) / S;
            qx = (m01 + m10) / S;
            qy = 0.25F * S;
            qz = (m12 + m21) / S;
        } else {
            float S = (float)Math.sqrt(1.0 + m22 - m00 - m11) * 2;
            qw = (m10 - m01) / S;
            qx = (m02 + m20) / S;
            qy = (m12 + m21) / S;
            qz = 0.25F * S;
        }
        return new float[] {qw, qx, qy, qz};
    }
}
