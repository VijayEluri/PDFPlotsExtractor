#!/usr/bin/python
#
#  An algorithm trying to adjust parameters of the extractor one by one (unlike a genetic algorithm)
#  We specify ranges and type (int, float) of all the parameters, order them in the importance order
#  Then we divide a parameter space into 1/10 slices and find optimal value ... subsequently we search
#  2 adjacent intervals dividing them in 10 pieces to find a more fine-grained maximum
#  As a quality function we use recall+precission

# Unlike in the genetic algorithm, we assume that parameters are independent.
# We set initial optimum to manually adjusted values and search for better results.
# Algorithm can be rerun afterwards on the results of previous execution


#
# The execution of this optimiser might take many days/hours and can be subjects to various
# unpredicted conditions which can potentially harm the processing.
# The execution state is saved after atomic stages and can be further resumed at any stage
# (after a process crash). This is achieved by making the initialisation phase of every loop
# separate from the loop construct ( while loops to iterate over integers) and by replacing
# all local variables with fields inside of a serialisable state object.
# When the program is called without arguments, the initialisation phase is executed, otherwise
# state is restored from a presaved state (stored in a file) and the initialisation is skipped
# so that the calculation can start from the prevois state

import os
import cPickle
import subprocess
import datetime
import re


# THE CONFIGURATION PART ... EDIT THIS TO CHANGE THE BEHAVIOUR

manager_address = "localhost:12345"
input_directory ='/opt/ppraczyk/splited'
output_directory = '/opt/ppraczyk/training_output'
descriptions_file = '/opt/ppraczyk/splited/figures.data.py'
temp_dir = '/opt/ppraczyk/temp'

execution_state_dir  = '/opt/ppraczyk/training_execution_state'


optimisable_parameters = [
    ["minimal_aspect_ratio", "float", 0.001, 1, 0.1],
    ["minimal_graphical_area_fraction", "float", 0, 1, 0.15],
    ["minimal_figure_width", "float", 0, 1, 0.15],
    ["minimal_column_width", "float", 0, 1, 0.25],
    ["minimal_figure_operations_number", "integer", 0, 1000, 10],
    ["minimal_vertical_separator_height", "float", 0, 1, 0.4],
    ["minimal_figure_graphical_operations_fraction", "float", 0, 1, 0.10],
    ["vertical_graphical_margin", "float", 0, 0.1, 0.02],
    ["vertical_emptiness_radius", "float", 0, 0.05, 0.005],
    ["maximum_non_breaking_fraction", "float", 0, 0.05, 0.005],
    ["vertical_plot_text_margin", "float", 0, 0.005, 0.00065],
    ["horizontal_text_margin", "float", 0, 0.5, 0.05],
    ["minimal_margin_width", "float", 0, 1, 0.3],
    ["colour_emptiness_threshold", "integer", 0, 255, 10],
    ["minimal_figure_height", "float", 0, 0.5, 0.1],
    ["horizontal_graphical_margin", "float", 0, 1, 0.1],
    ["horizontal_emptiness_radius", "float", 0, 0.1, 0.01],
    ["vertical_text_margin", "float", 0, 0.1, 0.005],
    ["horizontal_plot_text_margin", "float", 0, 0.1, 0.01],
    ["minimal_figure_graphical_operations_number", "integer", 0, 100, 4],
    ["maximal_inclusion_height", "float", 0, 1, 0.10]
]

# arguments outside of optimization - to be included in teh configuration
ignored_arguments = [("generate_debug_information", "false"),
                     ("generate_svg", "true"),
                     ("page_scale", "2"),
                     ("empty_pixel_colour_r","255"),
                     ("generate_plot_provenance","true"),
                     ("empty_pixel_colour_g","255"),
                     ("empty_pixel_colour_b","255")]



# END OF THE CONFIGURATION SECTION


def calculateQuality(precission, recall):
    pass

def formatConfiguration(parameters):
    return "\n".join(["%s=%s" % (param[0], param[1]) for param in parameters])

def writeConfiguration(parameters, fileName):
    f = open(fileName, "w")
    f.write(formatConfiguration(parameters))
    f.close()

def getTimestampString():
    n = datetime.datetime.now()
    return ("%i_%i_%i_%i_%i_%i_%i") % (n.year, n.month, n.day, n.hour, n.minute, n.second, n.microsecond)

def runTest(parameters, name):
    """Executes a single test and return the evaluation measure - pair recall, precission"""
    config_fname = "%s/%s_configfile" % (execution_state_dir, name, )
    results_fname = "%s/%s_results" % (execution_state_dir, name, )

    writeConfiguration(parameters, config_fname)

    test_unique_name =  name
    global manager_address
    global input_directory
    global output_directory
    global descriptions_file
    global temp_dir

    exec_params = ['./runTest.py',
                   '--controller=%s' % (manager_address,),
                   '--directory=%s'% ( input_directory, ),
                   '--output=%s'%(output_directory, ),
                   '--test=%s' % (test_unique_name, ),
                   '--description=%s' % (descriptions_file, ),
                   "--temp=%s" % (temp_dir, ),
                   "--config=%s" % (config_fname, )]


    process = subprocess.Popen(exec_params, stdout = subprocess.PIPE, stderr = subprocess.PIPE)
    print "waiting...",

    results, err = process.communicate()


#    results = process.stdout.read()

#    print process.stderr.read()
    print "finished"

    # extracting information from the standard output
    try:
        expected = float(re.search("Expected figures: ([0-9]+)", results).groups()[0])
        detected = float(re.search("Correctly detected figures: ([0-9]+)", results).groups()[0])
        misdetected = float(re.search("Misdetected figures: ([0-9]+)", results).groups()[0])
    except:
        print "Incorrect output of the extraction process ! Make sure that the resources manager is running under the specified address: %s stderr: %s" % (results, err)
        raise Exception("Unable to extract figures")

    if expected == 0:
        if detected == expected:
            recall = 1
        else:
            recall = 0
    else:
        recall = detected / expected

    if detected + misdetected == 0:
        precission = 1
    else:
        precission = detected / (detected + misdetected)


    print "expected: %s, extracted: %s, misextracted: %s ... precission: %s recall: %s" \
        % (str(expected), str(detected), str(misdetected), str(precission), str(recall))

    f = open(results_fname, "w")
    f.write("Obtained recall: %s precission: %s\n" % (str(recall), str(precission)))
    f.write("stdout: %s\n\n\n-------------------------------------------------------------------------------------------------------------------------------------------\n\n\nstderr: %s" % (results, err))
    f.close()
    return recall, precission

   #./runTest.py -c localhost:123498 -d /opt/ppraczyk/small_sample/ -o /opt/ppraczyk/small_sample_output/ --test=fourth_test -e /opt/ppraczyk/small_sample/figures.data.py --temp=/opt/ppraczyk/temp




def generateParameterValues(parameter):
    step = (float(parameter[3]) - float(parameter[2])) / 10
    result = [float(parameter[2]) + x*step for x in xrange(11)]
    if not float(parameter[4]) in result:
        result = result + [parameter[4]]
        result.sort()

    if parameter[1] == "integer":
        # in this case we remove duplicates and convert to integers
        result = [int(x) for x in result]
        new_result = []
        prev = None
        for element in result:
            if prev != element:
                new_result.append(element)
                prev = element
        result = new_result
    return result


class ProcessingState(object):
    def __init__(self):
        self.seq = 0
        self.resuming = False

    def save_to_file(self):
        # if we have reached this place, we are not resuming any more ... first save will always be equal to the read
        self.resuming = False
        self.seq += 1

        if not os.path.exists(execution_state_dir):
            os.mkdir(execution_state_dir)
        f = open(self.get_file_path(), "w")
        f.write(cPickle.dumps(self))
        f.close()

    @staticmethod
    def restore_from_file():
        """reading the newest snapshot"""

        try:
            files = filter(lambda fname: fname.endswith("_snapshot"), os.listdir(execution_state_dir))
            files.sort(reverse = True)
            for file_name in files:
                try:
                    print "Trying to load the saved state from the file %s" % (file_name, )
                    f = open(os.path.join(execution_state_dir, file_name), "r")
                    c = f.read()
                    f.close()
                    instance =  cPickle.loads(c)
                    instance.resuming = True
                    return instance
                except:
                    print "Failed reading state from the file %s" % (file_name, )

        except:
            # probably no snapshots directory ... exception can be thrown at this level
            pass

        # there is no newest file
        print "No state to resume from. Starting from scratch"
        return ProcessingState()

    def get_unique_name(self):
        """Returns a unuqie identifier allowing to order snapshots"""
        return "%0*d" % (5, self.seq,)

    def get_file_path(self):
        return "%s/%s_snapshot" % (execution_state_dir, self.get_unique_name())

def optimiseParameter(state, parameter, otherParameters):
    if not state.resuming:
        state.recDepth = 0
        state.processing_param = parameter
        state.opt_ind = None
        state.min_rec, state.min_prec = -1, -1
        state.otherParameters = otherParameters
#    from pydev import pydevd
#    pydevd.settrace()
    print "*Beginning the optimisation of a new parameter %s of the type %s and values in the interval [%s, %s]" % (parameter[0], parameter[1], str(parameter[2]), str(parameter[3]))

    while state.recDepth <= 1:
        if not state.resuming:
            state.opt_ind = None
            state.min_rec, state.min_prec = -1, -1

            state.possibleValues = generateParameterValues(state.processing_param)
            state.val_ind = 0
        while state.val_ind < len(state.possibleValues):
            print ("***Testing the parameter value %s=%s" % (state.processing_param[0], state.possibleValues[state.val_ind])),

            # saving the state... entering the risky and long part of the execution
            state.save_to_file()
            test_name = "%s_%s" % (state.get_unique_name(), getTimestampString())
            state.act_rec, state.act_prec = runTest(state.otherParameters + [(state.processing_param[0], str(state.possibleValues[state.val_ind]))], test_name)
            print "finished tunning test"
#            print "  recall=%s, precission=%s" % (str(state.act_rec), str(state.act_prec))
            if state.act_rec + state.act_prec > state.min_rec + state.min_prec:
                state.opt_ind = state.val_ind
                state.min_rec = state.act_rec
                state.min_prec = state.act_prec
            state.val_ind += 1
        state.opt_val = state.possibleValues[state.opt_ind]

        # generating a new processing param
        if state.opt_ind == 0:
            state.new_min = state.possibleValues[0]
            state.new_max = state.possibleValues[1]
        elif state.opt_ind == len(state.possibleValues) -1:
            state.new_min =  state.possibleValues[-2]
            state.new_max = state.possibleValues[-1]
        else:
            state.new_min = state.possibleValues[state.opt_ind -1]
            state.new_max = state.possibleValues[state.opt_ind + 1]
        state.recDepth += 1
        state.processing_param = [state.processing_param[0], state.processing_param[1], state.new_min, state.new_max, state.possibleValues[state.opt_ind]]

    print "*finished optimising the parameter. The optimal value is %s=%s yielding recall=%s, precission=%s" % (state.processing_param[0], state.opt_val, state.min_rec, state.min_prec)

    return state.opt_val, state.min_rec, state.min_prec


def transformToNormalParameters(params):
    """Takes a detailed list of parameters (containing types and intervals) and transforms
    into key, value pairs"""
    return map(lambda x: (x[0], x[4]), params)

def main():

    state = ProcessingState.restore_from_file()

    if not state.resuming:
        state.pind = 0
        state.optimisable_parameters = optimisable_parameters # we store it in the state just in case of resuming after the parameters list modification
        state.ignored_arguments = ignored_arguments
    else:
        print "Skipping the global initialisation"

    while state.pind < len(state.optimisable_parameters):
        remaining = transformToNormalParameters(state.optimisable_parameters[:state.pind] + state.optimisable_parameters[state.pind+1:]) + state.ignored_arguments
        optimal_value, optimal_recall, optimal_precission = optimiseParameter(state, state.optimisable_parameters[state.pind], remaining)
        state.optimisable_parameters[state.pind][4] = optimal_value
        state.pind += 1

    print "optimal solution found !"
    print formatConfiguration(transformToNormalParameters(state.optimisable_parameters))

if __name__=="__main__":
    main()
