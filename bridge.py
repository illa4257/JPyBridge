import socket, threading, typing, sys, traceback
import types

BRIDGE_LOG = False

# OPERATIONS
RETURN = 0
THROW = 1
CONTAINS = 2
GET = 3
DICT_GET = 4
SET = 5
DICT_SET = 6
CALL = 7
EXEC = 8
RELEASE = 9

# TYPES
LAMBDA = 10
JAVA_OBJECT = 11
PYTHON_OBJECT = 12
PYTHON_LIST = PYTHON_OBJECT + 1

# BYTES
B_CALL = CALL.to_bytes(1)

B_LAMBDA = LAMBDA.to_bytes(1)
B_JAVA_OBJECT = JAVA_OBJECT.to_bytes(1)
B_PYTHON_OBJECT = PYTHON_OBJECT.to_bytes(1)
B_PYTHON_LIST = PYTHON_LIST.to_bytes(1)

BRIDGE_BASE_TYPES = [ list, dict ]

global __is, __out

def __get_by_key(dictionary, val):
    return next((k for k, v in dictionary.items() if v == val), None)

def reflection_call(func, args: list):
    if type(func) in BRIDGE_BASE_TYPES:
        return func
    e = 'func('
    if len(args) > 0:
        e += 'args[0]'
        for i in range(1, len(args)):
            e += f', args[{i}]'
    return eval(e + ')')

class JavaObject:
    def __init__(self, bridge: 'Bridge', java_id: int):
        self.__bridge = bridge
        self.java_id = java_id

    def call_method_list(self, name: str, args: list|tuple):
        with self.__bridge.out_locker:
            self.__bridge.init_operation()
            self.__bridge.outputStream.write(B_CALL)
            self.__bridge.write_object(self)
            b_m = name.encode(self.__bridge.charset)
            self.__bridge.outputStream.write(len(b_m).to_bytes(4, 'big'))
            self.__bridge.outputStream.write(b_m)
            self.__bridge.outputStream.write(len(args).to_bytes(4, 'big'))
            for arg in args:
                self.__bridge.write_object(arg)
        return self.__bridge.wait_response()

    def call_method(self, name: str, *args):
        return self.call_method_list(name, args)

    def __str__(self):
        with self.__bridge.out_locker:
            self.__bridge.init_operation()
            self.__bridge.outputStream.write(B_CALL)
            self.__bridge.write_object(self)
            b_to_str = "toString".encode(self.__bridge.charset)
            self.__bridge.outputStream.write(len(b_to_str).to_bytes(4, 'big'))
            self.__bridge.outputStream.write(b_to_str)
            self.__bridge.outputStream.write((0).to_bytes(4, 'big'))
        return self.__bridge.wait_response()

class BridgeThread(threading.Thread):
    def __init__(self, bridge: 'Bridge', thread_id: int):
        threading.Thread.__init__(self)
        self.bridge = bridge
        self.thread_id = thread_id
        self.cond = threading.Condition()
        self.counter = 1

    def run(self):
        self.bridge.perform(int.from_bytes(self.bridge.inputStream.read(1), 'big'), True)

class Bridge:
    def __init__(self, input_stream: typing.BinaryIO, output_stream: typing.BinaryIO, charset: str = "utf-8"):
        self.inputStream = input_stream
        self.outputStream = output_stream
        self.charset = charset

        self.out_locker = threading.Lock()

        self.__java_objects = {}
        self.__py_objects = {}
        self.__lockers = {}
        self.threads = {}
        self.module = types.ModuleType("JVM")

        while True:
            id = int.from_bytes(self.inputStream.read(8), 'big')
            if id not in self.threads:
                self.threads[id] = th = BridgeThread(self, id)
                with th.cond:
                    th.start()
                    th.cond.wait()
            else:
                th = self.threads[id]
                l = self.get_locker(th)
                with l:
                    l.notify_all()
                    l.wait()

    def read_object(self):
        c = int.from_bytes(self.inputStream.read(1), 'big')
        if c == 0: # NULL
            return None
        if c == 1: # BOOL
            return self.inputStream.read(1) == b'\x01'
        if c == 2: # BYTE
            return self.inputStream.read(1)
        if c == 3: # SHORT
            return int.from_bytes(self.inputStream.read(2), 'big')
        if c == 4: # INT
            return int.from_bytes(self.inputStream.read(4), 'big')
        if c == 5: # LONG
            return int.from_bytes(self.inputStream.read(8), 'big')
        if c == 9: # STR
            return self.inputStream.read(int.from_bytes(self.inputStream.read(4), 'big')).decode(self.charset)
        if c == JAVA_OBJECT:
            obj_id = int.from_bytes(self.inputStream.read(8), 'big')
            if obj_id not in self.__java_objects:
                self.__java_objects[obj_id] = JavaObject(self, obj_id)
            return self.__java_objects[obj_id]
        if c == PYTHON_OBJECT or c == PYTHON_LIST: # PYTHON OBJECT
            return self.__py_objects[int.from_bytes(self.inputStream.read(8), 'big')]
        sys.stderr.write(f'[JPyBridge] Unknown type code: {c}\n')
        sys.stderr.flush()
        return None

    def write_object(self, o):
        if o is None:
            self.outputStream.write(b'\x00')
            return
        if type(o) == bool:
            if o:
                self.outputStream.write(b'\x01\x01')
            else:
                self.outputStream.write(b'\x01\x00')
            return
        if type(o) == int:
            self.outputStream.write(b'\x05')
            self.outputStream.write(o.to_bytes(8, 'big'))
            return
        if type(o) == str:
            self.outputStream.write(b'\x09')
            o = o.encode(self.charset)
            self.outputStream.write(len(o).to_bytes(4, 'big'))
            self.outputStream.write(o)
            return
        if isinstance(o, JavaObject):
            self.outputStream.write(B_JAVA_OBJECT)
            self.outputStream.write(o.java_id.to_bytes(8, 'big'))
            return
        self.__py_objects[id(o)] = o
        if type(o) == list:
            self.outputStream.write(B_PYTHON_LIST)
        else:
            self.outputStream.write(B_PYTHON_OBJECT)
        self.outputStream.write(id(o).to_bytes(8, 'big'))
        if BRIDGE_LOG:
            print("Unknown class type", type(o).__name__, o)
            #traceback.print_stack()

    def get_locker(self, thread: threading.Thread) -> threading.Condition:
        if isinstance(thread, BridgeThread):
            return thread.cond
        raise BaseException(f'Unknown thread type: {str(thread)}')

    def init_operation(self):
        th = threading.current_thread()
        if isinstance(th, BridgeThread):
            self.outputStream.write(th.thread_id.to_bytes(8, 'big'))

    def wait_response(self):
        th = threading.current_thread()
        l = self.get_locker(th)
        with l:
            self.outputStream.flush()
            while True:
                l.wait()
                c = int.from_bytes(self.inputStream.read(1), 'big')
                if c == RETURN:
                    r = self.read_object()
                    l.notify_all()
                    return r
                if c == THROW:
                    r = self.read_object()
                    l.notify_all()
                    raise BaseException(r)
                self.perform(c)

    def perform(self, c: int, is_runner: bool = False):
        d1 = None
        throw = False
        result = None
        thread = threading.current_thread()
        cond = self.get_locker(thread)

        if c == CONTAINS:
            d1 = [ self.read_object(), self.read_object() ]
        elif c == GET:
            d1 = [
                self.read_object(),
                self.inputStream.read(int.from_bytes(self.inputStream.read(4), 'big')).decode(self.charset)
            ]
        elif c == DICT_GET:
            d1 = [
                self.read_object(),
                self.read_object()
            ]
        elif c == SET:
            d1 = [
                self.read_object(),
                self.inputStream.read(int.from_bytes(self.inputStream.read(4), 'big')).decode(self.charset),
                self.read_object()
            ]
        elif c == DICT_SET:
            d1 = [
                self.read_object(),
                self.read_object(),
                self.read_object()
            ]
        elif c == CALL:
            obj = self.read_object()
            name = self.inputStream.read(int.from_bytes(self.inputStream.read(4), 'big')).decode(self.charset)
            args = []
            for n in range(int.from_bytes(self.inputStream.read(4), 'big')):
                args.append(self.read_object())
            d1 = [ obj, name, args ]
        elif c == EXEC:
            d1 = self.inputStream.read(int.from_bytes(self.inputStream.read(4), 'big')).decode(self.charset)
        elif c == RELEASE:
            py_id = int.from_bytes(self.inputStream.read(8), 'big')
            self.__py_objects.pop(py_id)
        else:
            sys.stderr.write(f'[JPyBridge] Unknown code: {c}\n')
            sys.stderr.flush()
            traceback.print_stack()

        with cond:
            cond.notify_all()

        try:
            if c == CONTAINS:
                result = d1[1] in d1[0]
            elif c == GET:
                if d1[0] is None:
                    result = eval(d1[1])
                else:
                    result = eval(f'd1[0].{d1[1]}')
            elif c == DICT_GET:
                result = eval(f'd1[0][d1[1]]')
            elif c == SET:
                if d1[0] is None:
                    exec(f'global {d1[1]}\n{d1[1]} = d1[2]')
                else:
                    exec(f'd1[0].{d1[1]} = d1[2]')
            elif c == DICT_SET:
                exec(f'd1[0][d1[1]] = d1[2]')
            elif c == CALL:
                if d1[0] is None:
                    result = reflection_call(eval(d1[1]), d1[2])
                else:
                    result = reflection_call(eval(f'd1[0].{d1[1]}'), d1[2])
            elif c == EXEC:
                e = {}
                exec(d1, e)
                if 'result' in e:
                    result = e['result']
        except:
            throw = True
            result = sys.exc_info()[1]

        if is_runner and isinstance(thread, BridgeThread):
            if thread.counter == 1:
                thread.bridge.threads.pop(thread.thread_id)
            else:
                thread.counter -= 1

        if c == RELEASE:
            return

        with self.out_locker:
            self.init_operation()
            if throw:
                self.outputStream.write(b'\x01')
            else:
                self.outputStream.write(b'\x00')
            self.write_object(result)
            self.outputStream.flush()

__p = 0
for a in sys.argv[1:]:
    if __p == 0:
        if a == '--con':
            __p = 1
    elif __p == 1:
        __p = 0
        s = socket.socket()
        s.connect(('127.0.0.1', int(a)))
        __is = s.makefile("rb")
        __out = s.makefile("wb")

if '__is' not in globals():
    __is = sys.stdin.buffer
    __out = sys.stdout.buffer

Bridge(__is, __out)