use jni::JNIEnv;
use jni::objects::JClass;
use jni::sys::jint;

#[unsafe(no_mangle)]
pub extern "system" fn Java_com_example_prostats_data_BatteryHealthEstimator_calculateHealthScoreNative(
    mut _env: JNIEnv,
    _class: JClass,
    cycles: jint,
    current_capacity: jint,
    design_capacity: jint,
) -> jint {
    let capacity_ratio = if design_capacity > 0 {
        let ratio = current_capacity as f32 / design_capacity as f32;
        if ratio < 0.0 { 0.0 } else if ratio > 1.1 { 1.1 } else { ratio }
    } else {
        1.0
    };

    let cycle_factor = 1.0 - (cycles as f32 / 1500.0);
    let cycle_factor = if cycle_factor < 0.3 { 0.3 } else if cycle_factor > 1.0 { 1.0 } else { cycle_factor };

    let score = ((capacity_ratio * 0.7 + cycle_factor * 0.3) * 100.0) as i32;
    if score < 0 { 0 } else if score > 100 { 100 } else { score }
}
