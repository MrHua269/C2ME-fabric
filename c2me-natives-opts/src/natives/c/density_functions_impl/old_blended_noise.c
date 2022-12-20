#include "../../include/density_functions.h"
#include "../../include/common_maths.h"
#include "../../include/noise.h"

static __attribute__((pure)) double c2me_natives_dfi_old_blended_noise_single_op(void *instance, int x, int y, int z) {
    interpolated_sampler_data *data = instance;
    return math_noise_perlin_interpolated_sample(data, x, y, z);
}

static __attribute__((pure)) void
c2me_natives_dfi_old_blended_noise_multi_op(void *instance, double *res, noise_pos *poses, size_t length) {
    interpolated_sampler_data *data = instance;
    for (size_t i = 0; i < length; i++) {
        res[i] = math_noise_perlin_interpolated_sample(data, poses[i].x, poses[i].y, poses[i].z);
    }
}

density_function_impl_data __attribute__((malloc)) *
c2me_natives_create_dfi_old_blended_noise_data(interpolated_sampler_data *sampler) {
    density_function_impl_data *dfi = malloc(sizeof(density_function_impl_data));
    dfi->instance = sampler;
    dfi->single_op = c2me_natives_dfi_old_blended_noise_single_op;
    dfi->multi_op = c2me_natives_dfi_old_blended_noise_multi_op;
    return dfi;
}
